package com.chatagg.db;

import com.chatagg.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    private static final Logger log = LoggerFactory.getLogger(UserDao.class);
    private final DatabaseManager db;

    public UserDao(DatabaseManager db) {
        this.db = db;
    }

    public AppUser findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, display_name, role, created_at FROM app_user WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find user by username", e);
        }
        return null;
    }

    public List<AppUser> findAll() {
        String sql = "SELECT id, username, password_hash, display_name, role, created_at FROM app_user ORDER BY id";
        List<AppUser> users = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AppUser user = mapRow(rs);
                user.setPasswordHash(null); // never expose hash in listings
                users.add(user);
            }
        } catch (SQLException e) {
            log.error("Failed to list users", e);
        }
        return users;
    }

    public AppUser insert(String username, String passwordHash, String displayName, String role) {
        String sql = "INSERT INTO app_user (username, password_hash, display_name, role) VALUES (?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, displayName);
            ps.setString(4, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AppUser user = new AppUser();
                    user.setId(rs.getLong("id"));
                    user.setUsername(username);
                    user.setDisplayName(displayName);
                    user.setRole(role);
                    user.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to insert user", e);
        }
        return null;
    }

    public boolean updatePassword(long id, String passwordHash) {
        String sql = "UPDATE app_user SET password_hash = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to update password", e);
            return false;
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM app_user WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to delete user", e);
            return false;
        }
    }

    public boolean hasAnyUsers() {
        String sql = "SELECT EXISTS(SELECT 1 FROM app_user)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            log.error("Failed to check for users", e);
        }
        return false;
    }

    public int countAdmins() {
        String sql = "SELECT COUNT(*) FROM app_user WHERE role = 'admin'";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Failed to count admins", e);
        }
        return 0;
    }

    public AppUser findById(long id) {
        String sql = "SELECT id, username, password_hash, display_name, role, created_at FROM app_user WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find user by id", e);
        }
        return null;
    }

    private AppUser mapRow(ResultSet rs) throws SQLException {
        AppUser user = new AppUser();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setDisplayName(rs.getString("display_name"));
        user.setRole(rs.getString("role"));
        user.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        return user;
    }
}
