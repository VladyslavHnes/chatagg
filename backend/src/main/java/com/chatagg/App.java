package com.chatagg;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.chatagg.api.*;
import com.chatagg.config.AppConfig;
import com.chatagg.db.DatabaseManager;
import com.chatagg.db.UserDao;
import com.chatagg.model.AppUser;
import com.chatagg.ocr.OcrService;
import com.chatagg.sync.SyncEventBus;
import com.chatagg.sync.SyncService;
import com.chatagg.telegram.MessageParser;
import com.chatagg.telegram.TelegramClient;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        DatabaseManager db = new DatabaseManager(config);

        // User management
        UserDao userDao = new UserDao(db);
        seedAdminUser(userDao, config);

        // Core components
        TelegramClient telegramClient = new TelegramClient(config);
        MessageParser messageParser = new MessageParser();
        OcrService ocrService = new OcrService(config);
        SyncService syncService = new SyncService(config, db, telegramClient, messageParser, ocrService);
        SyncEventBus syncEventBus = new SyncEventBus();
        syncService.setEventBus(syncEventBus);

        // Shared response cache (5-minute TTL, explicitly invalidated on mutations)
        ResponseCache responseCache = new ResponseCache(5 * 60 * 1000L);

        // Controllers
        SyncController syncController = new SyncController(syncService, telegramClient, responseCache);
        AuthController authController = new AuthController(telegramClient);
        BookController bookController = new BookController(db, responseCache);
        QuoteController quoteController = new QuoteController(db);
        ReviewController reviewController = new ReviewController(db);
        StatsController statsController = new StatsController(db, responseCache);
        UserController userController = new UserController(userDao);

        // Resolve frontend dir: try ../frontend (when run from backend/) or frontend/ (from project root)
        String frontendPath = java.nio.file.Path.of("frontend").toAbsolutePath().toString();
        if (!java.nio.file.Files.isDirectory(java.nio.file.Path.of(frontendPath))) {
            frontendPath = java.nio.file.Path.of("../frontend").toAbsolutePath().normalize().toString();
        }
        String resolvedFrontend = frontendPath;

        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add(resolvedFrontend, Location.EXTERNAL);
        });

        // Authentication
        app.before("/api/*", new AuthMiddleware(userDao));

        // Role-based access control
        app.before("/api/*", ctx -> {
            String method = ctx.method().name();
            String path = ctx.path();

            // GET /api/me — allowed for all authenticated users
            if ("GET".equals(method) && "/api/me".equals(path)) {
                return;
            }

            // GET /api/users — admin only
            if ("GET".equals(method) && "/api/users".equals(path)) {
                AppUser user = ctx.attribute("currentUser");
                if (user == null || !user.isAdmin()) {
                    throw new ForbiddenResponse("Admin access required");
                }
                return;
            }

            // All other GET requests — allowed for all authenticated users
            if ("GET".equals(method)) {
                return;
            }

            // POST, PUT, DELETE — admin only
            AppUser user = ctx.attribute("currentUser");
            if (user == null || !user.isAdmin()) {
                throw new ForbiddenResponse("Admin access required");
            }
        });

        app.get("/", ctx -> ctx.redirect("/index.html"));

        // User management
        app.get("/api/me", userController::getCurrentUser);
        app.get("/api/users", userController::listUsers);
        app.post("/api/users", userController::createUser);
        app.delete("/api/users/{id}", userController::deleteUser);
        app.put("/api/users/{id}/password", userController::resetPassword);

        // Auth
        app.get("/api/auth/status", authController::getStatus);
        app.post("/api/auth/code", authController::submitCode);

        // US1: Books & Sync
        app.get("/api/books", bookController::listBooks);
        app.post("/api/books", bookController::createBook);
        app.get("/api/books/{id}", bookController::getBook);
        app.get("/api/books/{id}/neighbors", bookController::getNeighbors);
        app.delete("/api/books/{id}", bookController::deleteBook);
        app.put("/api/books/{id}/genre", bookController::updateBookGenre);
        app.put("/api/books/{id}/title", bookController::updateBookTitle);
        app.put("/api/books/{id}/impression", bookController::updateBookImpression);
        app.post("/api/sync", syncController::triggerSync);
        app.post("/api/enrich", syncController::triggerEnrich);
        app.get("/api/sync/events", ctx -> {
            ctx.res().setContentType("text/event-stream");
            ctx.res().setCharacterEncoding("UTF-8");
            ctx.res().setHeader("Cache-Control", "no-cache");
            ctx.res().setHeader("X-Accel-Buffering", "no");
            ctx.res().setHeader("Connection", "keep-alive");
            ctx.res().flushBuffer();

            java.io.PrintWriter writer = ctx.res().getWriter();
            syncEventBus.setWriter(writer);
            try {
                long deadline = System.currentTimeMillis() + 15 * 60 * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(500);
                    writer.write(": ping\n\n");
                    writer.flush();
                    if (writer.checkError()) break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                syncEventBus.clearWriter();
            }
        });

        // US2: Quotes & Search & Review
        app.get("/api/books/{id}/quotes", quoteController::listByBook);
        app.get("/api/quotes/browse", quoteController::browseQuotes);
        app.get("/api/quotes/random", quoteController::randomQuote);
        app.get("/api/quotes/search", quoteController::search);
        app.get("/api/quotes/{id}", quoteController::getQuote);
        app.put("/api/quotes/{id}/move", quoteController::moveQuote);
        app.put("/api/quotes/{id}/edit", quoteController::editQuote);
        app.delete("/api/quotes/{id}", quoteController::deleteQuote);
        app.put("/api/quotes/{id}/unlink", quoteController::unlinkQuote);
        app.get("/api/photos/{id}", quoteController::servePhoto);
        app.get("/api/review", reviewController::listFlagged);
        app.put("/api/review/{id}/approve", reviewController::approve);
        app.put("/api/review/{id}/dismiss", reviewController::dismiss);
        app.post("/api/review/merge", reviewController::mergeBooks);

        // Authors
        app.get("/api/authors", bookController::listAuthors);
        app.get("/api/authors/{id}", bookController::getAuthor);
        app.put("/api/authors/{id}", bookController::editAuthor);
        app.delete("/api/authors/{id}", bookController::deleteAuthor);
        app.post("/api/authors/{id}/books", bookController::addBookToAuthor);
        app.post("/api/authors/merge", bookController::mergeAuthors);

        // US3: Filtering & Enrichment
        app.get("/api/genres", bookController::listGenres);
        app.get("/api/countries", bookController::listCountries);

        // US4: Statistics
        app.get("/api/stats", statsController::getStats);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            app.stop();
            db.close();
        }));

        app.start(config.appPort);
        log.info("Chatagg started on port {}", config.appPort);
    }

    private static void seedAdminUser(UserDao userDao, AppConfig config) {
        if (!userDao.hasAnyUsers()) {
            String hash = BCrypt.withDefaults().hashToString(12, config.appPassword.toCharArray());
            userDao.insert("admin", hash, "Admin", "admin");
            log.info("Seeded default admin user (username: admin)");
        }
    }
}
