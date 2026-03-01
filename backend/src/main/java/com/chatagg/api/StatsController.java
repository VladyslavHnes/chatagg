package com.chatagg.api;

import com.chatagg.db.DatabaseManager;
import io.javalin.http.Context;

import java.sql.*;
import java.util.*;

public class StatsController {

    private final DatabaseManager db;
    private final ResponseCache cache;

    public StatsController(DatabaseManager db, ResponseCache cache) {
        this.db = db;
        this.cache = cache;
    }

    public void getStats(Context ctx) {
        Object cached = cache.get("stats");
        if (cached != null) { ctx.json(cached); return; }

        Map<String, Object> stats = new LinkedHashMap<>();

        try (Connection conn = db.getConnection()) {
            // Total books
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM book");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("total_books", rs.getInt(1));
            }

            // Total quotes (approved only)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM quote WHERE review_status = 'approved'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("total_quotes", rs.getInt(1));
            }

            // Total photos
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM photo");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("total_photos", rs.getInt(1));
            }

            // Total authors
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM author");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("total_authors", rs.getInt(1));
            }

            // Books per month
            List<Map<String, Object>> booksPerMonth = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TO_CHAR(announcement_date, 'YYYY-MM') AS month, COUNT(*) AS count " +
                    "FROM book WHERE announcement_date IS NOT NULL " +
                    "GROUP BY month ORDER BY month");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("month", rs.getString("month"));
                    entry.put("count", rs.getInt("count"));
                    booksPerMonth.add(entry);
                }
            }
            stats.put("books_per_month", booksPerMonth);

            // Genre distribution
            List<Map<String, Object>> genreDist = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(genre, 'Unknown') AS genre, COUNT(*) AS count " +
                    "FROM book GROUP BY genre ORDER BY count DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("genre", rs.getString("genre"));
                    entry.put("count", rs.getInt("count"));
                    genreDist.add(entry);
                }
            }
            stats.put("genre_distribution", genreDist);

            // Books per country
            List<Map<String, Object>> booksCountryDist = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(a.country, 'Unknown') AS country, COUNT(DISTINCT b.id) AS count " +
                    "FROM book b " +
                    "LEFT JOIN book_author ba ON b.id = ba.book_id " +
                    "LEFT JOIN author a ON ba.author_id = a.id " +
                    "GROUP BY a.country ORDER BY count DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("country", rs.getString("country"));
                    entry.put("count", rs.getInt("count"));
                    booksCountryDist.add(entry);
                }
            }
            stats.put("books_per_country", booksCountryDist);

            // Fiction per country
            List<Map<String, Object>> fictionCountryDist = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.country, COUNT(DISTINCT b.id) AS count " +
                    "FROM book b " +
                    "JOIN book_author ba ON b.id = ba.book_id " +
                    "JOIN author a ON ba.author_id = a.id " +
                    "WHERE b.genre = 'Fiction' AND a.country IS NOT NULL " +
                    "GROUP BY a.country ORDER BY count DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("country", rs.getString("country"));
                    entry.put("count", rs.getInt("count"));
                    fictionCountryDist.add(entry);
                }
            }
            stats.put("fiction_per_country", fictionCountryDist);

            // Non-fiction per country
            List<Map<String, Object>> nonfictionCountryDist = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.country, COUNT(DISTINCT b.id) AS count " +
                    "FROM book b " +
                    "JOIN book_author ba ON b.id = ba.book_id " +
                    "JOIN author a ON ba.author_id = a.id " +
                    "WHERE b.genre = 'Non-fiction' AND a.country IS NOT NULL " +
                    "GROUP BY a.country ORDER BY count DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("country", rs.getString("country"));
                    entry.put("count", rs.getInt("count"));
                    nonfictionCountryDist.add(entry);
                }
            }
            stats.put("nonfiction_per_country", nonfictionCountryDist);

            // Authors per country
            List<Map<String, Object>> authorsCountryDist = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(country, 'Unknown') AS country, COUNT(*) AS count " +
                    "FROM author " +
                    "GROUP BY country ORDER BY count DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("country", rs.getString("country"));
                    entry.put("count", rs.getInt("count"));
                    authorsCountryDist.add(entry);
                }
            }
            stats.put("authors_per_country", authorsCountryDist);

            // Most popular authors (by book count)
            List<Map<String, Object>> topAuthors = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.name, COALESCE(a.country, 'Unknown') AS country, COUNT(ba.book_id) AS count " +
                    "FROM author a " +
                    "JOIN book_author ba ON a.id = ba.author_id " +
                    "GROUP BY a.id, a.name, a.country " +
                    "ORDER BY count DESC " +
                    "LIMIT 20");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", rs.getString("name"));
                    entry.put("country", rs.getString("country"));
                    entry.put("count", rs.getInt("count"));
                    topAuthors.add(entry);
                }
            }
            stats.put("top_authors", topAuthors);

            // Average books per month
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*)::float / GREATEST(COUNT(DISTINCT TO_CHAR(announcement_date, 'YYYY-MM')), 1) AS avg " +
                    "FROM book WHERE announcement_date IS NOT NULL");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("avg_books_per_month", Math.round(rs.getDouble("avg") * 10.0) / 10.0);
            }

            // Books per year
            List<Map<String, Object>> booksPerYear = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TO_CHAR(announcement_date, 'YYYY') AS year, COUNT(*) AS count " +
                    "FROM book WHERE announcement_date IS NOT NULL " +
                    "GROUP BY year ORDER BY year");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("year", rs.getString("year"));
                    entry.put("count", rs.getInt("count"));
                    booksPerYear.add(entry);
                }
            }
            stats.put("books_per_year", booksPerYear);

            // Books per day (for heatmap)
            List<Map<String, Object>> booksPerDay = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TO_CHAR(announcement_date, 'YYYY-MM-DD') AS day, COUNT(*) AS count " +
                    "FROM book WHERE announcement_date IS NOT NULL " +
                    "GROUP BY day ORDER BY day");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("day", rs.getString("day"));
                    entry.put("count", rs.getInt("count"));
                    booksPerDay.add(entry);
                }
            }
            stats.put("books_per_day", booksPerDay);

            // Reading streak (consecutive days with books, ending today or yesterday)
            int streak = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT announcement_date::date AS d FROM book " +
                    "WHERE announcement_date IS NOT NULL ORDER BY d DESC");
                 ResultSet rs = ps.executeQuery()) {
                java.time.LocalDate expected = java.time.LocalDate.now();
                boolean started = false;
                while (rs.next()) {
                    java.time.LocalDate d = rs.getDate("d").toLocalDate();
                    if (!started) {
                        // Allow streak to start from today or yesterday
                        if (d.equals(expected) || d.equals(expected.minusDays(1))) {
                            expected = d;
                            started = true;
                        } else {
                            break;
                        }
                    }
                    if (d.equals(expected)) {
                        streak++;
                        expected = expected.minusDays(1);
                    } else if (d.isBefore(expected)) {
                        break;
                    }
                }
            }
            stats.put("reading_streak", streak);

            // Last book read
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT b.id, b.title, b.announcement_date, b.genre " +
                    "FROM book b WHERE b.announcement_date IS NOT NULL " +
                    "ORDER BY b.announcement_date DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> lastBook = new LinkedHashMap<>();
                    lastBook.put("id", rs.getLong("id"));
                    lastBook.put("title", rs.getString("title"));
                    lastBook.put("date", rs.getDate("announcement_date").toString());
                    lastBook.put("genre", rs.getString("genre"));
                    stats.put("last_book", lastBook);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute stats", e);
        }

        cache.put("stats", stats);
        ctx.json(stats);
    }
}
