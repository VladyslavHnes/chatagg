package com.chatagg.db;

import com.chatagg.model.Quote;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuoteDao {

    private final DatabaseManager db;

    public QuoteDao(DatabaseManager db) {
        this.db = db;
    }

    public long insert(Quote quote) {
        String sql = "INSERT INTO quote (book_id, text_content, source_type, telegram_message_id, " +
                "telegram_message_date, ocr_confidence, photo_id, review_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (quote.getBookId() != null) {
                ps.setLong(1, quote.getBookId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, quote.getTextContent());
            ps.setString(3, quote.getSourceType());
            ps.setLong(4, quote.getTelegramMessageId());
            ps.setTimestamp(5, Timestamp.from(quote.getTelegramMessageDate()));
            if (quote.getOcrConfidence() != null) {
                ps.setFloat(6, quote.getOcrConfidence());
            } else {
                ps.setNull(6, Types.REAL);
            }
            if (quote.getPhotoId() != null) {
                ps.setLong(7, quote.getPhotoId());
            } else {
                ps.setNull(7, Types.BIGINT);
            }
            ps.setString(8, quote.getReviewStatus());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Quote> findByBookId(long bookId) {
        String sql = "SELECT * FROM quote WHERE book_id = ? ORDER BY telegram_message_date";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Quote> quotes = new ArrayList<>();
                while (rs.next()) {
                    quotes.add(mapQuote(rs));
                }
                return quotes;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Quote> search(String query, int offset, int limit) {
        String sql = "SELECT q.* FROM quote q " +
                "WHERE q.search_vector @@ plainto_tsquery('simple', ?) " +
                "ORDER BY ts_rank(q.search_vector, plainto_tsquery('simple', ?)) DESC " +
                "LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query);
            ps.setString(2, query);
            ps.setInt(3, limit);
            ps.setInt(4, offset * limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Quote> quotes = new ArrayList<>();
                while (rs.next()) {
                    quotes.add(mapQuote(rs));
                }
                return quotes;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Quote> findFlagged(int offset, int limit) {
        String sql = "SELECT * FROM quote WHERE review_status = 'flagged' " +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset * limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Quote> quotes = new ArrayList<>();
                while (rs.next()) {
                    quotes.add(mapQuote(rs));
                }
                return quotes;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> searchPaginated(String query, int page, int size) {
        String countSql = "SELECT COUNT(*) FROM quote q " +
                "WHERE q.search_vector @@ plainto_tsquery('simple', ?)";
        String dataSql = "SELECT q.id, q.text_content, q.source_type, q.book_id, " +
                "b.title AS book_title, q.telegram_message_date, " +
                "ts_rank(q.search_vector, plainto_tsquery('simple', ?)) AS rank " +
                "FROM quote q " +
                "LEFT JOIN book b ON q.book_id = b.id " +
                "WHERE q.search_vector @@ plainto_tsquery('simple', ?) " +
                "ORDER BY rank DESC " +
                "LIMIT ? OFFSET ?";

        int total;
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setString(1, query);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                ps.setString(1, query);
                ps.setString(2, query);
                ps.setInt(3, size);
                ps.setInt(4, page * size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", rs.getLong("id"));
                        item.put("text_content", rs.getString("text_content"));
                        item.put("source_type", rs.getString("source_type"));
                        long bookId = rs.getLong("book_id");
                        item.put("book_id", rs.wasNull() ? null : bookId);
                        item.put("book_title", rs.getString("book_title"));
                        item.put("telegram_message_date", rs.getTimestamp("telegram_message_date") != null
                                ? rs.getTimestamp("telegram_message_date").toInstant().toString() : null);
                        item.put("rank", rs.getDouble("rank"));
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Fetch authors for all items in a single query (avoids N+1)
        fetchAuthorsForItems(items);

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("query", query);
        return result;
    }

    public Map<String, Object> findFlaggedPaginated(int page, int size) {
        // Count query
        String countSql = "SELECT COUNT(*) FROM quote WHERE review_status = 'flagged'";
        int total;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Data query
        String dataSql = "SELECT * FROM quote WHERE review_status = 'flagged' " +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Quote> items = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapQuote(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public void updateReviewStatus(long id, String status) {
        String sql = "UPDATE quote SET review_status = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateTextContent(long id, String correctedText) {
        String sql = "UPDATE quote SET text_content = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correctedText);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM quote WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void unlinkFromBook(long id) {
        String sql = "UPDATE quote SET book_id = NULL WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBookId(long id, long bookId) {
        String sql = "UPDATE quote SET book_id = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> browseApproved(String query, int page, int size) {
        boolean hasQuery = query != null && !query.isBlank();

        String countSql = hasQuery
                ? "SELECT COUNT(*) FROM quote WHERE review_status = 'approved' AND search_vector @@ plainto_tsquery('simple', ?)"
                : "SELECT COUNT(*) FROM quote WHERE review_status = 'approved'";
        String dataSql = hasQuery
                ? "SELECT q.id, q.text_content, q.source_type, q.photo_id, q.book_id, " +
                  "b.title AS book_title, q.telegram_message_date " +
                  "FROM quote q LEFT JOIN book b ON q.book_id = b.id " +
                  "WHERE q.review_status = 'approved' AND q.search_vector @@ plainto_tsquery('simple', ?) " +
                  "ORDER BY ts_rank(q.search_vector, plainto_tsquery('simple', ?)) DESC LIMIT ? OFFSET ?"
                : "SELECT q.id, q.text_content, q.source_type, q.photo_id, q.book_id, " +
                  "b.title AS book_title, q.telegram_message_date " +
                  "FROM quote q LEFT JOIN book b ON q.book_id = b.id " +
                  "WHERE q.review_status = 'approved' " +
                  "ORDER BY q.telegram_message_date DESC NULLS LAST, q.id DESC LIMIT ? OFFSET ?";

        int total;
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                if (hasQuery) ps.setString(1, query);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                int idx = 1;
                if (hasQuery) {
                    ps.setString(idx++, query);
                    ps.setString(idx++, query);
                }
                ps.setInt(idx++, size);
                ps.setInt(idx, page * size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", rs.getLong("id"));
                        item.put("source_type", rs.getString("source_type"));
                        long photoId = rs.getLong("photo_id");
                        item.put("photo_id", rs.wasNull() ? null : photoId);
                        item.put("text_content", rs.getString("text_content"));
                        long bookId = rs.getLong("book_id");
                        item.put("book_id", rs.wasNull() ? null : bookId);
                        item.put("book_title", rs.getString("book_title"));
                        item.put("telegram_message_date", rs.getTimestamp("telegram_message_date") != null
                                ? rs.getTimestamp("telegram_message_date").toInstant().toString() : null);
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Fetch authors for all items in a single query (avoids N+1)
        fetchAuthorsForItems(items);

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> findByIdWithBook(long id) {
        String sql = "SELECT q.id, q.source_type, q.photo_id, q.text_content, " +
                "q.book_id, b.title AS book_title, q.telegram_message_date " +
                "FROM quote q LEFT JOIN book b ON q.book_id = b.id WHERE q.id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> result = new HashMap<>();
                result.put("id", rs.getLong("id"));
                result.put("source_type", rs.getString("source_type"));
                long photoId = rs.getLong("photo_id");
                result.put("photo_id", rs.wasNull() ? null : photoId);
                result.put("text_content", rs.getString("text_content"));
                long bookId = rs.getLong("book_id");
                result.put("book_id", rs.wasNull() ? null : bookId);
                result.put("book_title", rs.getString("book_title"));
                result.put("telegram_message_date", rs.getTimestamp("telegram_message_date") != null
                        ? rs.getTimestamp("telegram_message_date").toInstant().toString() : null);

                fetchAuthorsForItems(List.of(result));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> findRandom() {
        String sql = "SELECT q.id, q.source_type, q.photo_id, q.text_content, " +
                "q.book_id, b.title AS book_title " +
                "FROM quote q " +
                "LEFT JOIN book b ON q.book_id = b.id " +
                "WHERE q.review_status = 'approved' " +
                "ORDER BY RANDOM() LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("id", rs.getLong("id"));
            result.put("source_type", rs.getString("source_type"));
            long photoId = rs.getLong("photo_id");
            result.put("photo_id", rs.wasNull() ? null : photoId);
            result.put("text_content", rs.getString("text_content"));
            long bookId = rs.getLong("book_id");
            result.put("book_id", rs.wasNull() ? null : bookId);
            result.put("book_title", rs.getString("book_title"));

            fetchAuthorsForItems(List.of(result));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Fetches authors for a list of quote/search result items in a single IN query. */
    private void fetchAuthorsForItems(List<Map<String, Object>> items) {
        List<Long> bookIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Object bookIdObj = item.get("book_id");
            if (bookIdObj != null) bookIds.add((long) bookIdObj);
            item.put("authors", new ArrayList<String>());
        }
        if (bookIds.isEmpty()) return;

        String placeholders = String.join(",", bookIds.stream().map(id -> "?").toList());
        String sql = "SELECT ba.book_id, a.name FROM author a " +
                "JOIN book_author ba ON a.id = ba.author_id WHERE ba.book_id IN (" + placeholders + ")";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < bookIds.size(); i++) ps.setLong(i + 1, bookIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                Map<Long, List<String>> byBook = new HashMap<>();
                while (rs.next()) {
                    byBook.computeIfAbsent(rs.getLong("book_id"), k -> new ArrayList<>())
                          .add(rs.getString("name"));
                }
                for (Map<String, Object> item : items) {
                    Object bookIdObj = item.get("book_id");
                    if (bookIdObj != null) {
                        List<String> authors = byBook.getOrDefault((long) bookIdObj, new ArrayList<>());
                        item.put("authors", authors);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Quote mapQuote(ResultSet rs) throws SQLException {
        Quote quote = new Quote();
        quote.setId(rs.getLong("id"));
        long bookId = rs.getLong("book_id");
        quote.setBookId(rs.wasNull() ? null : bookId);
        quote.setTextContent(rs.getString("text_content"));
        quote.setSourceType(rs.getString("source_type"));
        quote.setTelegramMessageId(rs.getLong("telegram_message_id"));
        quote.setTelegramMessageDate(rs.getTimestamp("telegram_message_date") != null
                ? rs.getTimestamp("telegram_message_date").toInstant() : null);
        float ocrConfidence = rs.getFloat("ocr_confidence");
        quote.setOcrConfidence(rs.wasNull() ? null : ocrConfidence);
        long photoId = rs.getLong("photo_id");
        quote.setPhotoId(rs.wasNull() ? null : photoId);
        quote.setReviewStatus(rs.getString("review_status"));
        quote.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : null);
        return quote;
    }
}
