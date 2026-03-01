package com.chatagg.api;

import com.chatagg.sync.SyncService;
import com.chatagg.sync.SyncService.SyncResult;
import com.chatagg.telegram.TelegramClient;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final TelegramClient telegramClient;
    private final ResponseCache cache;

    public SyncController(SyncService syncService, TelegramClient telegramClient, ResponseCache cache) {
        this.syncService = syncService;
        this.telegramClient = telegramClient;
        this.cache = cache;
    }

    public void triggerEnrich(Context ctx) {
        try {
            syncService.enrichBooks();
            cache.invalidateAll();
            ctx.json(Map.of("status", "done"));
        } catch (Exception e) {
            log.error("Enrichment failed", e);
            ctx.status(500).json(Map.of("error", "Enrichment failed: " + e.getMessage()));
        }
    }

    public void triggerSync(Context ctx) {
        if (syncService.isSyncing()) {
            ctx.status(409).json(Map.of("error", "Sync already in progress"));
            return;
        }

        try {
            SyncResult result = syncService.sync();
            cache.invalidateAll();
            ctx.json(Map.of(
                    "new_messages", result.newMessages(),
                    "books_found", result.booksFound(),
                    "quotes_found", result.quotesFound(),
                    "photos_processed", result.photosProcessed(),
                    "flagged_items", result.flaggedItems(),
                    "duration_seconds", result.durationSeconds()
            ));
        } catch (Exception e) {
            // Check if the root cause is an auth requirement
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof TelegramClient.AuthRequiredException authEx) {
                    String authType = switch (authEx.getAuthState()) {
                        case WAITING_CODE -> "code";
                        case WAITING_PASSWORD -> "password";
                        default -> "unknown";
                    };
                    ctx.status(200).json(Map.of(
                            "auth_required", true,
                            "auth_type", authType
                    ));
                    return;
                }
                cause = cause.getCause();
            }
            log.error("Sync failed", e);
            ctx.status(500).json(Map.of("error", "Sync failed: " + e.getMessage()));
        }
    }
}
