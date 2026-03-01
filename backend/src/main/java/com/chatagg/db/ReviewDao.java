package com.chatagg.db;

import com.chatagg.model.ReviewItem;

import java.sql.*;
import java.util.*;

public class ReviewDao {

    private final DatabaseManager db;

    public ReviewDao(DatabaseManager db) {
        this.db = db;
    }

    public Map<String, Object> findFlagged(int page, int size, String typeFilter) {
        List<ReviewItem> items = new ArrayList<>();
        int total = 0;
        int offset = (page - 1) * size;

        try (Connection conn = db.getConnection()) {
            // Query flagged quotes as review items
            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM quote q WHERE q.review_status = 'flagged'");
            StringBuilder dataSql = new StringBuilder(
                    "SELECT q.id, q.text_content, q.source_type, q.telegram_message_id, " +
                    "q.telegram_message_date, q.ocr_confidence, q.book_id, " +
                    "p.local_path AS photo_path, p.ocr_text, " +
                    "b.title AS suggested_book_title " +
                    "FROM quote q " +
                    "LEFT JOIN photo p ON q.photo_id = p.id " +
                    "LEFT JOIN book b ON q.book_id = b.id " +
                    "WHERE q.review_status = 'flagged'");

            if (typeFilter != null && !typeFilter.equals("all")) {
                String typeCondition = switch (typeFilter) {
                    case "quote" -> " AND q.source_type = 'text'";
                    case "message" -> " AND q.source_type = 'photo'";
                    case "duplicate" -> ""; // handled separately
                    default -> "";
                };
                countSql.append(typeCondition);
                dataSql.append(typeCondition);
            }

            // Count
            try (PreparedStatement ps = conn.prepareStatement(countSql.toString());
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getInt(1);
                }
            }

            // Data
            dataSql.append(" ORDER BY q.telegram_message_date DESC LIMIT ? OFFSET ?");
            try (PreparedStatement ps = conn.prepareStatement(dataSql.toString())) {
                ps.setInt(1, size);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ReviewItem item = new ReviewItem();
                        item.setId(rs.getLong("id"));

                        // Determine type based on source and confidence
                        Float confidence = rs.getFloat("ocr_confidence");
                        if (rs.wasNull()) confidence = null;
                        String sourceType = rs.getString("source_type");

                        if ("photo".equals(sourceType) && confidence != null && confidence < 70) {
                            item.setType("low_ocr");
                        } else if ("photo".equals(sourceType)) {
                            item.setType("unrecognized");
                        } else {
                            item.setType("orphan_quote");
                        }

                        item.setTelegramMessageId(rs.getLong("telegram_message_id"));
                        Timestamp msgDate = rs.getTimestamp("telegram_message_date");
                        item.setMessageDate(msgDate != null ? msgDate.toInstant() : null);
                        item.setRawText(rs.getString("text_content"));
                        item.setOcrText(rs.getString("ocr_text"));
                        item.setOcrConfidence(confidence);
                        item.setPhotoPath(rs.getString("photo_path"));

                        long bookId = rs.getLong("book_id");
                        if (!rs.wasNull()) {
                            item.setSuggestedBookId(bookId);
                            item.setSuggestedBookTitle(rs.getString("suggested_book_title"));
                        }

                        items.add(item);
                    }
                }
            }

            // Also include flagged telegram_messages (duplicate books)
            if (typeFilter == null || "all".equals(typeFilter) || "duplicate".equals(typeFilter)) {
                String tmCountSql = "SELECT COUNT(*) FROM telegram_message WHERE processing_status = 'flagged'";
                try (PreparedStatement ps = conn.prepareStatement(tmCountSql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total += rs.getInt(1);
                    }
                }

                String tmSql = "SELECT id, telegram_message_id, message_date, raw_text " +
                        "FROM telegram_message WHERE processing_status = 'flagged' " +
                        "ORDER BY message_date DESC LIMIT ? OFFSET ?";
                try (PreparedStatement ps = conn.prepareStatement(tmSql)) {
                    ps.setInt(1, size);
                    ps.setInt(2, Math.max(0, offset - items.size()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ReviewItem item = new ReviewItem();
                            item.setId(rs.getLong("id"));
                            item.setType("duplicate_book");
                            item.setTelegramMessageId(rs.getLong("telegram_message_id"));
                            Timestamp msgDate = rs.getTimestamp("message_date");
                            item.setMessageDate(msgDate != null ? msgDate.toInstant() : null);
                            item.setRawText(rs.getString("raw_text"));
                            items.add(item);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find flagged items", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items.stream().map(this::toMap).toList());
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public void approve(long quoteId, String correctedText, Long bookId) {
        try (Connection conn = db.getConnection()) {
            StringBuilder sql = new StringBuilder("UPDATE quote SET review_status = 'approved'");
            List<Object> params = new ArrayList<>();

            if (correctedText != null) {
                sql.append(", text_content = ?");
                params.add(correctedText);
            }
            if (bookId != null) {
                sql.append(", book_id = ?");
                params.add(bookId);
            }
            sql.append(" WHERE id = ?");
            params.add(quoteId);

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to approve review item", e);
        }
    }

    public void dismiss(long quoteId) {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE quote SET review_status = 'dismissed' WHERE id = ?")) {
                ps.setLong(1, quoteId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to dismiss review item", e);
        }
    }

    public void mergeBooks(long keepId, List<Long> mergeIds) {
        if (mergeIds == null || mergeIds.isEmpty()) {
            return;
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Reassign quotes from merged books to the keep book
                String placeholders = String.join(",", mergeIds.stream().map(id -> "?").toList());

                String reassignQuotes = "UPDATE quote SET book_id = ? WHERE book_id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(reassignQuotes)) {
                    ps.setLong(1, keepId);
                    for (int i = 0; i < mergeIds.size(); i++) {
                        ps.setLong(i + 2, mergeIds.get(i));
                    }
                    ps.executeUpdate();
                }

                // Move author links
                String reassignAuthors = "INSERT INTO book_author (book_id, author_id) " +
                        "SELECT ?, author_id FROM book_author WHERE book_id IN (" + placeholders + ") " +
                        "ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(reassignAuthors)) {
                    ps.setLong(1, keepId);
                    for (int i = 0; i < mergeIds.size(); i++) {
                        ps.setLong(i + 2, mergeIds.get(i));
                    }
                    ps.executeUpdate();
                }

                // Delete author links for merged books
                String deleteAuthorLinks = "DELETE FROM book_author WHERE book_id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(deleteAuthorLinks)) {
                    for (int i = 0; i < mergeIds.size(); i++) {
                        ps.setLong(i + 1, mergeIds.get(i));
                    }
                    ps.executeUpdate();
                }

                // Delete merged books
                String deleteBooks = "DELETE FROM book WHERE id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(deleteBooks)) {
                    for (int i = 0; i < mergeIds.size(); i++) {
                        ps.setLong(i + 1, mergeIds.get(i));
                    }
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to merge books", e);
        }
    }

    private Map<String, Object> toMap(ReviewItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("type", item.getType());
        map.put("telegram_message_id", item.getTelegramMessageId());
        map.put("message_date", item.getMessageDate() != null ? item.getMessageDate().toString() : null);
        map.put("raw_text", item.getRawText());
        map.put("ocr_text", item.getOcrText());
        map.put("ocr_confidence", item.getOcrConfidence());
        map.put("photo_path", item.getPhotoPath());
        map.put("suggested_book_id", item.getSuggestedBookId());
        map.put("suggested_book_title", item.getSuggestedBookTitle());
        return map;
    }
}
