package com.chatagg.db;

import com.chatagg.model.Author;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuthorDao {

    private final DatabaseManager db;

    public AuthorDao(DatabaseManager db) {
        this.db = db;
    }

    public long insertOrFind(String name) {
        return insertOrFind(name, null);
    }

    public long insertOrFind(String name, String country) {
        String findSql = "SELECT id FROM author WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String insertSql = "INSERT INTO author (name, country) VALUES (?, ?) RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, name);
            if (country != null && !country.isBlank()) {
                ps.setString(2, country.trim());
            } else {
                ps.setNull(2, java.sql.Types.VARCHAR);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Author findById(long id) {
        String sql = "SELECT * FROM author WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAuthor(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Author> findByBookId(long bookId) {
        String sql = "SELECT a.* FROM author a " +
                "JOIN book_author ba ON a.id = ba.author_id " +
                "WHERE ba.book_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Author> authors = new ArrayList<>();
                while (rs.next()) {
                    authors.add(mapAuthor(rs));
                }
                return authors;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void linkToBook(long bookId, long authorId) {
        String sql = "INSERT INTO book_author (book_id, author_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setLong(2, authorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateCountry(long id, String country) {
        String sql = "UPDATE author SET country = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, country);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateAuthor(long id, String name, String country) {
        String sql = "UPDATE author SET name = ?, country = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, country);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void mergeAuthors(long keepId, List<Long> mergeIds) {
        try (Connection conn = db.getConnection()) {
            // Reassign book associations, skip if already linked
            String reassignSql = "UPDATE book_author SET author_id = ? WHERE author_id = ? " +
                    "AND book_id NOT IN (SELECT book_id FROM book_author WHERE author_id = ?)";
            try (PreparedStatement ps = conn.prepareStatement(reassignSql)) {
                for (long mergeId : mergeIds) {
                    ps.setLong(1, keepId);
                    ps.setLong(2, mergeId);
                    ps.setLong(3, keepId);
                    ps.executeUpdate();
                }
            }
            // Delete merged authors (CASCADE removes leftover book_author rows)
            StringBuilder deleteSql = new StringBuilder("DELETE FROM author WHERE id IN (");
            for (int i = 0; i < mergeIds.size(); i++) {
                if (i > 0) deleteSql.append(",");
                deleteSql.append("?");
            }
            deleteSql.append(")");
            try (PreparedStatement ps = conn.prepareStatement(deleteSql.toString())) {
                for (int i = 0; i < mergeIds.size(); i++) {
                    ps.setLong(i + 1, mergeIds.get(i));
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM author WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Author> findWithoutCountry() {
        String sql = "SELECT * FROM author WHERE country IS NULL";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Author> authors = new ArrayList<>();
                while (rs.next()) {
                    authors.add(mapAuthor(rs));
                }
                return authors;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Author> findWithoutCountryByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM author WHERE country IS NULL AND id IN (");
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
                List<Author> authors = new ArrayList<>();
                while (rs.next()) {
                    authors.add(mapAuthor(rs));
                }
                return authors;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Author> findAll() {
        String sql = "SELECT * FROM author ORDER BY name";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Author> authors = new ArrayList<>();
                while (rs.next()) {
                    authors.add(mapAuthor(rs));
                }
                return authors;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> findAllCountries() {
        String sql = "SELECT DISTINCT country FROM author WHERE country IS NOT NULL ORDER BY country";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> countries = new ArrayList<>();
                while (rs.next()) {
                    countries.add(rs.getString("country"));
                }
                return countries;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Author mapAuthor(ResultSet rs) throws SQLException {
        Author author = new Author();
        author.setId(rs.getLong("id"));
        author.setName(rs.getString("name"));
        author.setCountry(rs.getString("country"));
        author.setWikidataId(rs.getString("wikidata_id"));
        author.setOpenlibraryId(rs.getString("openlibrary_id"));
        author.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : null);
        return author;
    }
}
