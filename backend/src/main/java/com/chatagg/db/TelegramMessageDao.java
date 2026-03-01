package com.chatagg.db;

import com.chatagg.model.TelegramMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TelegramMessageDao {

    private final DatabaseManager db;

    public TelegramMessageDao(DatabaseManager db) {
        this.db = db;
    }

    public long insert(TelegramMessage msg) {
        String sql = "INSERT INTO telegram_message (telegram_message_id, chat_id, message_date, " +
                "message_type, raw_text, processing_status) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (telegram_message_id) DO NOTHING RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, msg.getTelegramMessageId());
            ps.setLong(2, msg.getChatId());
            ps.setTimestamp(3, Timestamp.from(msg.getMessageDate()));
            ps.setString(4, msg.getMessageType());
            ps.setString(5, msg.getRawText());
            ps.setString(6, msg.getProcessingStatus());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TelegramMessage findByTelegramMessageId(long telegramMessageId) {
        String sql = "SELECT * FROM telegram_message WHERE telegram_message_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTelegramMessage(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TelegramMessage> findByStatus(String status) {
        String sql = "SELECT * FROM telegram_message WHERE processing_status = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                List<TelegramMessage> messages = new ArrayList<>();
                while (rs.next()) {
                    messages.add(mapTelegramMessage(rs));
                }
                return messages;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateProcessingStatus(long id, String status) {
        String sql = "UPDATE telegram_message SET processing_status = ?, processed_at = now() WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean existsByTelegramMessageId(long telegramMessageId) {
        String sql = "SELECT 1 FROM telegram_message WHERE telegram_message_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private TelegramMessage mapTelegramMessage(ResultSet rs) throws SQLException {
        TelegramMessage msg = new TelegramMessage();
        msg.setId(rs.getLong("id"));
        msg.setTelegramMessageId(rs.getLong("telegram_message_id"));
        msg.setChatId(rs.getLong("chat_id"));
        msg.setMessageDate(rs.getTimestamp("message_date") != null
                ? rs.getTimestamp("message_date").toInstant() : null);
        msg.setMessageType(rs.getString("message_type"));
        msg.setRawText(rs.getString("raw_text"));
        msg.setProcessingStatus(rs.getString("processing_status"));
        msg.setProcessedAt(rs.getTimestamp("processed_at") != null
                ? rs.getTimestamp("processed_at").toInstant() : null);
        return msg;
    }
}
