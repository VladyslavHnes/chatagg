package com.chatagg.api;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.chatagg.db.UserDao;
import com.chatagg.model.AppUser;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class UserController {

    private final UserDao userDao;

    public UserController(UserDao userDao) {
        this.userDao = userDao;
    }

    public void getCurrentUser(Context ctx) {
        AppUser user = ctx.attribute("currentUser");
        ctx.json(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "display_name", user.getDisplayName(),
                "role", user.getRole(),
                "created_at", user.getCreatedAt().toString()
        ));
    }

    public void listUsers(Context ctx) {
        List<AppUser> users = userDao.findAll();
        ctx.json(users.stream().map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "display_name", u.getDisplayName(),
                "role", u.getRole(),
                "created_at", u.getCreatedAt().toString()
        )).toList());
    }

    public void createUser(Context ctx) {
        var body = ctx.bodyAsClass(CreateUserRequest.class);

        if (body.username == null || body.username.isBlank()) {
            ctx.status(400).result("Username is required");
            return;
        }
        if (body.password == null || body.password.isBlank()) {
            ctx.status(400).result("Password is required");
            return;
        }
        if (body.display_name == null || body.display_name.isBlank()) {
            ctx.status(400).result("Display name is required");
            return;
        }
        String role = body.role != null && body.role.equals("admin") ? "admin" : "user";

        if (userDao.findByUsername(body.username) != null) {
            ctx.status(409).result("Username already exists");
            return;
        }

        String hash = BCrypt.withDefaults().hashToString(12, body.password.toCharArray());
        AppUser created = userDao.insert(body.username, hash, body.display_name, role);
        if (created == null) {
            ctx.status(500).result("Failed to create user");
            return;
        }

        ctx.status(201).json(Map.of(
                "id", created.getId(),
                "username", created.getUsername(),
                "display_name", created.getDisplayName(),
                "role", created.getRole(),
                "created_at", created.getCreatedAt().toString()
        ));
    }

    public void deleteUser(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        AppUser currentUser = ctx.attribute("currentUser");

        if (currentUser.getId() == id) {
            ctx.status(400).result("Cannot delete yourself");
            return;
        }

        AppUser target = userDao.findById(id);
        if (target == null) {
            ctx.status(404).result("User not found");
            return;
        }

        if (target.isAdmin() && userDao.countAdmins() <= 1) {
            ctx.status(400).result("Cannot delete the last admin");
            return;
        }

        userDao.delete(id);
        ctx.status(204);
    }

    public void resetPassword(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        var body = ctx.bodyAsClass(ResetPasswordRequest.class);

        if (body.password == null || body.password.isBlank()) {
            ctx.status(400).result("Password is required");
            return;
        }

        AppUser target = userDao.findById(id);
        if (target == null) {
            ctx.status(404).result("User not found");
            return;
        }

        String hash = BCrypt.withDefaults().hashToString(12, body.password.toCharArray());
        userDao.updatePassword(id, hash);
        ctx.status(204);
    }

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String display_name;
        public String role;
    }

    public static class ResetPasswordRequest {
        public String password;
    }
}
