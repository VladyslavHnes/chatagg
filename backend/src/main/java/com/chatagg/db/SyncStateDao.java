package com.chatagg.db;

import com.chatagg.model.SyncState;

import java.sql.*;

public class SyncStateDao {

    private final DatabaseManager db;

    public SyncStateDao(DatabaseManager db) {
        this.db = db;
    }

    public SyncState getOrCreate(long channelChatId) {
        String findSql = "SELECT * FROM sync_state WHERE channel_chat_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setLong(1, channelChatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSyncState(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String insertSql = "INSERT INTO sync_state (channel_chat_id) VALUES (?) RETURNING *";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setLong(1, channelChatId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return mapSyncState(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateLastMessageId(long id, long lastMessageId) {
        String sql = "UPDATE sync_state SET last_message_id = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastMessageId);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateLastSyncAt(long id) {
        String sql = "UPDATE sync_state SET last_sync_at = now() WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void incrementProcessedCount(long id, long delta) {
        String sql = "UPDATE sync_state SET total_messages_processed = total_messages_processed + ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, delta);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private SyncState mapSyncState(ResultSet rs) throws SQLException {
        SyncState state = new SyncState();
        state.setId(rs.getLong("id"));
        state.setChannelChatId(rs.getLong("channel_chat_id"));
        long lastMsgId = rs.getLong("last_message_id");
        state.setLastMessageId(rs.wasNull() ? null : lastMsgId);
        state.setLastSyncAt(rs.getTimestamp("last_sync_at") != null
                ? rs.getTimestamp("last_sync_at").toInstant() : null);
        state.setTotalMessagesProcessed(rs.getLong("total_messages_processed"));
        return state;
    }
}
