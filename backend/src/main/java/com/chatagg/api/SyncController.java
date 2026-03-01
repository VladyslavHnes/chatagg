package com.chatagg.api;

import com.chatagg.sync.SyncService;
import com.chatagg.telegram.TelegramClient;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final ResponseCache cache;

    public SyncController(SyncService syncService, TelegramClient telegramClient, ResponseCache cache) {
        this.syncService = syncService;
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

        ctx.status(202).json(Map.of("status", "started"));
        Thread t = new Thread(() -> {
            try {
                syncService.sync();
                cache.invalidateAll();
            } catch (Exception e) {
                log.error("Async sync failed", e);
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
