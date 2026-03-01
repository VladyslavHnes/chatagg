package com.chatagg.db;

import com.chatagg.model.Photo;

import java.sql.*;

public class PhotoDao {

    private final DatabaseManager db;

    public PhotoDao(DatabaseManager db) {
        this.db = db;
    }

    public long insert(Photo photo) {
        String sql = "INSERT INTO photo (telegram_file_id, local_path, ocr_text, ocr_confidence) " +
                "VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, photo.getTelegramFileId());
            ps.setString(2, photo.getLocalPath());
            ps.setString(3, photo.getOcrText());
            if (photo.getOcrConfidence() != null) {
                ps.setFloat(4, photo.getOcrConfidence());
            } else {
                ps.setNull(4, Types.REAL);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Photo findById(long id) {
        String sql = "SELECT * FROM photo WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPhoto(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Photo findByTelegramFileId(String telegramFileId) {
        String sql = "SELECT * FROM photo WHERE telegram_file_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, telegramFileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPhoto(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Photo mapPhoto(ResultSet rs) throws SQLException {
        Photo photo = new Photo();
        photo.setId(rs.getLong("id"));
        photo.setTelegramFileId(rs.getString("telegram_file_id"));
        photo.setLocalPath(rs.getString("local_path"));
        photo.setOcrText(rs.getString("ocr_text"));
        float ocrConfidence = rs.getFloat("ocr_confidence");
        photo.setOcrConfidence(rs.wasNull() ? null : ocrConfidence);
        photo.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : null);
        return photo;
    }
}
