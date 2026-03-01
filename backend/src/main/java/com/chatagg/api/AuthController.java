package com.chatagg.api;

import com.chatagg.telegram.TelegramClient;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final TelegramClient telegramClient;

    public AuthController(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void getStatus(Context ctx) {
        String state = telegramClient.getAuthState().name().toLowerCase();
        ctx.json(Map.of("state", state));
    }

    public void submitCode(Context ctx) {
        String code = ctx.bodyAsClass(CodeRequest.class).code();
        if (code == null || code.isBlank()) {
            ctx.status(400).json(Map.of("error", "Code is required"));
            return;
        }

        TelegramClient.AuthState currentState = telegramClient.getAuthState();
        if (currentState != TelegramClient.AuthState.WAITING_CODE
                && currentState != TelegramClient.AuthState.WAITING_PASSWORD) {
            ctx.status(409).json(Map.of("error", "No authentication input is pending"));
            return;
        }

        log.info("Submitting auth input for state {}", currentState);
        telegramClient.submitAuthInput(code.trim());
        ctx.json(Map.of("status", "submitted"));
    }

    private record CodeRequest(String code) {}
}
