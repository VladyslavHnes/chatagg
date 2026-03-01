package com.chatagg.db;

import com.chatagg.model.Book;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookDao {

    private final DatabaseManager db;

    public BookDao(DatabaseManager db) {
        this.db = db;
    }

    public long insert(Book book) {
        String sql = "INSERT INTO book (title, review_note, genre, announcement_date, " +
                "telegram_message_id, cover_photo_path, metadata_source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getReviewNote());
            ps.setString(3, book.getGenre());
            ps.setTimestamp(4, Timestamp.from(book.getAnnouncementDate()));
            ps.setLong(5, book.getTelegramMessageId());
            ps.setString(6, book.getCoverPhotoPath());
            ps.setString(7, book.getMetadataSource());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Book findById(long id) {
        String sql = "SELECT * FROM book WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBook(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Book findByTelegramMessageId(long telegramMessageId) {
        String sql = "SELECT * FROM book WHERE telegram_message_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBook(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> findAll(int page, int size, String sort, String genre, String country,
                                        String titleFilter, String authorFilter) {
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (genre != null && !genre.isEmpty()) {
            whereClause.append(" AND b.genre = ?");
            params.add(genre);
        }
        if (country != null && !country.isEmpty()) {
            whereClause.append(" AND LOWER(a.country) LIKE LOWER(?)");
            params.add("%" + country + "%");
        }
        if (titleFilter != null && !titleFilter.isEmpty()) {
            whereClause.append(" AND LOWER(b.title) LIKE LOWER(?)");
            params.add("%" + titleFilter + "%");
        }
        if (authorFilter != null && !authorFilter.isEmpty()) {
            whereClause.append(" AND LOWER(a.name) LIKE LOWER(?)");
            params.add("%" + authorFilter + "%");
        }

        String orderBy = switch (sort != null ? sort : "date_desc") {
            case "date_asc" -> "b.announcement_date ASC";
            case "title_asc" -> "b.title ASC";
            case "title_desc" -> "b.title DESC";
            default -> "b.announcement_date DESC";
        };

        // Count query
        String countSql = "SELECT COUNT(DISTINCT b.id) FROM book b " +
                "LEFT JOIN book_author ba ON b.id = ba.book_id " +
                "LEFT JOIN author a ON ba.author_id = a.id " +
                "WHERE 1=1" + whereClause;

        int total;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Data query
        String dataSql = "SELECT b.id, b.title, b.review_note, b.genre, b.announcement_date, " +
                "b.telegram_message_id, " +
                "b.cover_photo_path, b.metadata_source, b.created_at, b.updated_at, " +
                "COALESCE((SELECT COUNT(*) FROM quote q WHERE q.book_id = b.id), 0) AS quote_count " +
                "FROM book b " +
                "LEFT JOIN book_author ba ON b.id = ba.book_id " +
                "LEFT JOIN author a ON ba.author_id = a.id " +
                "WHERE 1=1" + whereClause +
                " GROUP BY b.id " +
                "ORDER BY " + orderBy +
                " LIMIT ? OFFSET ?";

        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            int idx = 1;
            for (Object param : params) {
                ps.setObject(idx++, param);
            }
            ps.setInt(idx++, size);
            ps.setInt(idx, (page - 1) * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("title", rs.getString("title"));
                    item.put("review_note", rs.getString("review_note"));
                    item.put("genre", rs.getString("genre"));
                    item.put("announcement_date", rs.getTimestamp("announcement_date") != null
                            ? rs.getTimestamp("announcement_date").toInstant().toString() : null);
                    item.put("telegram_message_id", rs.getLong("telegram_message_id"));
                    item.put("cover_photo_path", rs.getString("cover_photo_path"));
                    item.put("metadata_source", rs.getString("metadata_source"));
                    item.put("quote_count", rs.getInt("quote_count"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Fetch authors for all books in a single query (avoids N+1)
        if (!items.isEmpty()) {
            List<Long> bookIds = items.stream().map(item -> (long) item.get("id")).toList();
            Map<Long, List<Map<String, Object>>> authorsByBookId = new HashMap<>();
            for (long id : bookIds) authorsByBookId.put(id, new ArrayList<>());
            String placeholders = String.join(",", bookIds.stream().map(id -> "?").toList());
            String authorSql = "SELECT ba.book_id, a.id, a.name, a.country FROM author a " +
                    "JOIN book_author ba ON a.id = ba.author_id WHERE ba.book_id IN (" + placeholders + ")";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(authorSql)) {
                for (int i = 0; i < bookIds.size(); i++) ps.setLong(i + 1, bookIds.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> author = new LinkedHashMap<>();
                        author.put("id", rs.getLong("id"));
                        author.put("name", rs.getString("name"));
                        author.put("country", rs.getString("country"));
                        authorsByBookId.get(rs.getLong("book_id")).add(author);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            for (Map<String, Object> item : items) {
                item.put("authors", authorsByBookId.get((long) item.get("id")));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public List<Map<String, Object>> findByAuthorId(long authorId) {
        String sql = "SELECT b.id, b.title, b.genre, b.announcement_date " +
                "FROM book b " +
                "JOIN book_author ba ON b.id = ba.book_id " +
                "WHERE ba.author_id = ? " +
                "ORDER BY b.announcement_date DESC NULLS LAST";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> books = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("title", rs.getString("title"));
                    m.put("genre", rs.getString("genre"));
                    m.put("announcement_date", rs.getTimestamp("announcement_date") != null
                            ? rs.getTimestamp("announcement_date").toInstant().toString() : null);
                    books.add(m);
                }
                return books;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long insertManual(String title, String genre, String date) {
        String sql = "INSERT INTO book (title, genre, announcement_date, telegram_message_id, metadata_source) VALUES (?, ?, ?, ?, 'manual') RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            if (date != null) {
                ps.setTimestamp(3, Timestamp.valueOf(java.time.LocalDate.parse(date).atStartOfDay()));
            } else {
                ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            }
            ps.setLong(4, -System.nanoTime());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Book> findPotentialDuplicates(String title, String authorName) {
        String sql = "SELECT DISTINCT b.* FROM book b " +
                "LEFT JOIN book_author ba ON b.id = ba.book_id " +
                "LEFT JOIN author a ON ba.author_id = a.id " +
                "WHERE LOWER(b.title) = LOWER(?)";
        List<Object> params = new ArrayList<>();
        params.add(title);

        if (authorName != null && !authorName.isEmpty()) {
            sql += " AND (a.name IS NULL OR LOWER(a.name) = LOWER(?))";
            params.add(authorName);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> books = new ArrayList<>();
                while (rs.next()) {
                    books.add(mapBook(rs));
                }
                return books;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findWithoutGenre() {
        String sql = "SELECT b.id, b.title, " +
                "(SELECT a.name FROM author a JOIN book_author ba ON a.id = ba.author_id WHERE ba.book_id = b.id LIMIT 1) AS author_name " +
                "FROM book b WHERE b.genre IS NULL";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> books = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("title", rs.getString("title"));
                    m.put("author_name", rs.getString("author_name"));
                    books.add(m);
                }
                return books;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> findWithoutGenreByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT b.id, b.title, " +
                "(SELECT a.name FROM author a JOIN book_author ba ON a.id = ba.author_id WHERE ba.book_id = b.id LIMIT 1) AS author_name " +
                "FROM book b WHERE b.genre IS NULL AND b.id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> books = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("title", rs.getString("title"));
                    m.put("author_name", rs.getString("author_name"));
                    books.add(m);
                }
                return books;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateTitle(long id, String title) {
        String sql = "UPDATE book SET title = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateGenre(long id, String genre, String metadataSource) {
        String sql = "UPDATE book SET genre = ?, metadata_source = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, genre);
            ps.setString(2, metadataSource);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public record BookNeighbors(Long prevId, String prevTitle, Long nextId, String nextTitle) {}

    public BookNeighbors findNeighbors(long bookId) {
        Book book = findById(bookId);
        if (book == null || book.getAnnouncementDate() == null) {
            return new BookNeighbors(null, null, null, null);
        }

        Long prevId = null;
        String prevTitle = null;
        Long nextId = null;
        String nextTitle = null;

        String prevSql = "SELECT id, title FROM book WHERE announcement_date < ? ORDER BY announcement_date DESC LIMIT 1";
        String nextSql = "SELECT id, title FROM book WHERE announcement_date > ? ORDER BY announcement_date ASC LIMIT 1";

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(prevSql)) {
                ps.setTimestamp(1, Timestamp.from(book.getAnnouncementDate()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        prevId = rs.getLong("id");
                        prevTitle = rs.getString("title");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(nextSql)) {
                ps.setTimestamp(1, Timestamp.from(book.getAnnouncementDate()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        nextId = rs.getLong("id");
                        nextTitle = rs.getString("title");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return new BookNeighbors(prevId, prevTitle, nextId, nextTitle);
    }

    public void delete(long bookId) {
        String sql = "DELETE FROM book WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateImpression(long id, String impression) {
        String sql = "UPDATE book SET impression = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (impression != null && !impression.isBlank()) {
                ps.setString(1, impression.trim());
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Book mapBook(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getLong("id"));
        book.setTitle(rs.getString("title"));
        book.setReviewNote(rs.getString("review_note"));
        book.setImpression(rs.getString("impression"));
        book.setGenre(rs.getString("genre"));
        book.setAnnouncementDate(rs.getTimestamp("announcement_date") != null
                ? rs.getTimestamp("announcement_date").toInstant() : null);
        book.setTelegramMessageId(rs.getLong("telegram_message_id"));
        book.setCoverPhotoPath(rs.getString("cover_photo_path"));
        book.setMetadataSource(rs.getString("metadata_source"));
        book.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : null);
        book.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toInstant() : null);
        return book;
    }
}
