package com.chatagg.api;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.chatagg.db.UserDao;
import com.chatagg.model.AppUser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;

import java.util.Base64;

public class AuthMiddleware implements Handler {

    private final UserDao userDao;

    public AuthMiddleware(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void handle(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            ctx.header("WWW-Authenticate", "Basic realm=\"chatagg\"");
            throw new UnauthorizedResponse("Authentication required");
        }

        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        String[] parts = decoded.split(":", 2);
        if (parts.length < 2) {
            ctx.header("WWW-Authenticate", "Basic realm=\"chatagg\"");
            throw new UnauthorizedResponse("Invalid credentials");
        }

        String username = parts[0];
        String password = parts[1];

        AppUser user = userDao.findByUsername(username);
        if (user == null) {
            ctx.header("WWW-Authenticate", "Basic realm=\"chatagg\"");
            throw new UnauthorizedResponse("Invalid credentials");
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            ctx.header("WWW-Authenticate", "Basic realm=\"chatagg\"");
            throw new UnauthorizedResponse("Invalid credentials");
        }

        ctx.attribute("currentUser", user);
    }
}
