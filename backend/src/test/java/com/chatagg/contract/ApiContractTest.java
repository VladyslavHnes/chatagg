package com.chatagg.contract;

import com.chatagg.api.*;
import com.chatagg.config.AppConfig;
import com.chatagg.db.BookDao;
import com.chatagg.db.DatabaseManager;
import com.chatagg.integration.TestDatabaseHelper;
import com.chatagg.model.Book;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiContractTest {

    private static final String TEST_PASSWORD = "testpass";
    private static final String AUTH_HEADER = "Basic " +
            Base64.getEncoder().encodeToString(("user:" + TEST_PASSWORD).getBytes());

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseManager db;
    private Javalin app;
    private int port;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    @BeforeAll
    void setup() {
        AppConfig config = TestDatabaseHelper.configFrom(postgres);
        config.appPassword = TEST_PASSWORD;

        db = new DatabaseManager(config);

        BookController bookController = new BookController(db);
        QuoteController quoteController = new QuoteController(db);
        ReviewController reviewController = new ReviewController(db);
        StatsController statsController = new StatsController(db);

        app = Javalin.create();

        app.before("/api/*", new AuthMiddleware(config));

        // Books & Sync
        app.get("/api/books", bookController::listBooks);
        app.get("/api/books/{id}", bookController::getBook);

        // Dummy sync handler - returns a static SyncResult-like JSON
        app.post("/api/sync", ctx -> ctx.json(Map.of(
                "new_messages", 0,
                "books_found", 0,
                "quotes_found", 0,
                "photos_processed", 0,
                "flagged_items", 0,
                "duration_seconds", 0.0
        )));

        // Quotes & Search & Review
        app.get("/api/books/{id}/quotes", quoteController::listByBook);
        app.get("/api/quotes/search", quoteController::search);
        app.get("/api/review", reviewController::listFlagged);
        app.put("/api/review/{id}/approve", reviewController::approve);
        app.put("/api/review/{id}/dismiss", reviewController::dismiss);
        app.post("/api/review/merge", reviewController::mergeBooks);

        // Filtering & Enrichment
        app.get("/api/genres", bookController::listGenres);
        app.get("/api/countries", bookController::listCountries);

        // Statistics
        app.get("/api/stats", statsController::getStats);

        app.start(0);
        port = app.port();

        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }

    @AfterAll
    void teardown() {
        if (app != null) app.stop();
        if (db != null) db.close();
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", AUTH_HEADER)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getNoAuth(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postNoAuth(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putNoAuth(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", AUTH_HEADER)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", AUTH_HEADER)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> parseJson(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private List<Object> parseJsonArray(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    // ---------------------------------------------------------------
    // Auth tests
    // ---------------------------------------------------------------

    @Test
    void allEndpoints_require_auth() throws Exception {
        assertEquals(401, getNoAuth("/api/books").statusCode());
        assertEquals(401, getNoAuth("/api/books/1").statusCode());
        assertEquals(401, getNoAuth("/api/books/1/quotes").statusCode());
        assertEquals(401, getNoAuth("/api/quotes/search?q=test").statusCode());
        assertEquals(401, getNoAuth("/api/stats").statusCode());
        assertEquals(401, getNoAuth("/api/genres").statusCode());
        assertEquals(401, getNoAuth("/api/countries").statusCode());
        assertEquals(401, postNoAuth("/api/sync", "{}").statusCode());
        assertEquals(401, getNoAuth("/api/review").statusCode());
        assertEquals(401, putNoAuth("/api/review/1/approve", "{}").statusCode());
        assertEquals(401, putNoAuth("/api/review/1/dismiss", "{}").statusCode());
        assertEquals(401, postNoAuth("/api/review/merge", "{}").statusCode());
    }

    // ---------------------------------------------------------------
    // GET /api/books
    // ---------------------------------------------------------------

    @Test
    void getBooks_returns_paginated_response() throws Exception {
        HttpResponse<String> response = get("/api/books");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertTrue(body.containsKey("items"), "Response must contain 'items'");
        assertTrue(body.containsKey("total"), "Response must contain 'total'");
        assertTrue(body.containsKey("page"), "Response must contain 'page'");
        assertTrue(body.containsKey("size"), "Response must contain 'size'");
        assertInstanceOf(List.class, body.get("items"));
        assertInstanceOf(Number.class, body.get("total"));
        assertInstanceOf(Number.class, body.get("page"));
        assertInstanceOf(Number.class, body.get("size"));
    }

    // ---------------------------------------------------------------
    // GET /api/books/{id}
    // ---------------------------------------------------------------

    @Test
    void getBook_notFound_returns404() throws Exception {
        HttpResponse<String> response = get("/api/books/99999");
        assertEquals(404, response.statusCode());
    }

    @Test
    void getBook_exists_returns200() throws Exception {
        BookDao bookDao = new BookDao(db);
        Book book = new Book();
        book.setTitle("Contract Test Book");
        book.setGenre("Fiction");
        book.setAnnouncementDate(Instant.parse("2026-01-15T12:00:00Z"));
        book.setTelegramMessageId(90001L);
        long bookId = bookDao.insert(book);

        HttpResponse<String> response = get("/api/books/" + bookId);
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertEquals(bookId, ((Number) body.get("id")).longValue());
        assertEquals("Contract Test Book", body.get("title"));
        assertEquals("Fiction", body.get("genre"));
        assertTrue(body.containsKey("authors"), "Response must contain 'authors'");
        assertTrue(body.containsKey("review_note"), "Response must contain 'review_note'");
        assertTrue(body.containsKey("announcement_date"), "Response must contain 'announcement_date'");
        assertTrue(body.containsKey("cover_photo_path"), "Response must contain 'cover_photo_path'");
        assertTrue(body.containsKey("metadata_source"), "Response must contain 'metadata_source'");
        assertTrue(body.containsKey("telegram_message_id"), "Response must contain 'telegram_message_id'");
        assertTrue(body.containsKey("quotes"), "Response must contain 'quotes'");
    }

    // ---------------------------------------------------------------
    // GET /api/books/{id}/quotes
    // ---------------------------------------------------------------

    @Test
    void getBookQuotes_returns_array() throws Exception {
        BookDao bookDao = new BookDao(db);
        Book book = new Book();
        book.setTitle("Quoteless Book");
        book.setAnnouncementDate(Instant.parse("2026-01-20T12:00:00Z"));
        book.setTelegramMessageId(90002L);
        long bookId = bookDao.insert(book);

        HttpResponse<String> response = get("/api/books/" + bookId + "/quotes");
        assertEquals(200, response.statusCode());

        List<Object> body = parseJsonArray(response.body());
        assertNotNull(body);
        assertTrue(body.isEmpty(), "Expected empty array for book with no quotes");
    }

    // ---------------------------------------------------------------
    // GET /api/quotes/search
    // ---------------------------------------------------------------

    @Test
    void searchQuotes_returns_searchResult() throws Exception {
        HttpResponse<String> response = get("/api/quotes/search?q=test");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertTrue(body.containsKey("items"), "Response must contain 'items'");
        assertTrue(body.containsKey("total"), "Response must contain 'total'");
        assertTrue(body.containsKey("page"), "Response must contain 'page'");
        assertTrue(body.containsKey("size"), "Response must contain 'size'");
        assertTrue(body.containsKey("query"), "Response must contain 'query'");
        assertInstanceOf(List.class, body.get("items"));
    }

    @Test
    void searchQuotes_emptyQuery_returnsEmpty() throws Exception {
        HttpResponse<String> response = get("/api/quotes/search?q=");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        List<?> items = (List<?>) body.get("items");
        assertTrue(items.isEmpty(), "Empty query should return empty items");
        assertEquals(0, ((Number) body.get("total")).intValue());
        assertEquals("", body.get("query"));
    }

    // ---------------------------------------------------------------
    // GET /api/stats
    // ---------------------------------------------------------------

    @Test
    void getStats_returns_statsShape() throws Exception {
        HttpResponse<String> response = get("/api/stats");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertTrue(body.containsKey("total_books"), "Response must contain 'total_books'");
        assertTrue(body.containsKey("total_quotes"), "Response must contain 'total_quotes'");
        assertTrue(body.containsKey("total_photos"), "Response must contain 'total_photos'");
        assertTrue(body.containsKey("average_reading_days"), "Response must contain 'average_reading_days'");
        assertTrue(body.containsKey("books_per_month"), "Response must contain 'books_per_month'");
        assertTrue(body.containsKey("genre_distribution"), "Response must contain 'genre_distribution'");
        assertTrue(body.containsKey("country_distribution"), "Response must contain 'country_distribution'");
        assertInstanceOf(Number.class, body.get("total_books"));
        assertInstanceOf(Number.class, body.get("total_quotes"));
        assertInstanceOf(Number.class, body.get("total_photos"));
        assertInstanceOf(List.class, body.get("books_per_month"));
        assertInstanceOf(List.class, body.get("genre_distribution"));
        assertInstanceOf(List.class, body.get("country_distribution"));
    }

    // ---------------------------------------------------------------
    // GET /api/genres
    // ---------------------------------------------------------------

    @Test
    void getGenres_returns_stringArray() throws Exception {
        HttpResponse<String> response = get("/api/genres");
        assertEquals(200, response.statusCode());

        List<Object> body = parseJsonArray(response.body());
        assertNotNull(body);
        // Every element should be a String
        for (Object item : body) {
            assertInstanceOf(String.class, item, "Each genre must be a string");
        }
    }

    // ---------------------------------------------------------------
    // GET /api/countries
    // ---------------------------------------------------------------

    @Test
    void getCountries_returns_stringArray() throws Exception {
        HttpResponse<String> response = get("/api/countries");
        assertEquals(200, response.statusCode());

        List<Object> body = parseJsonArray(response.body());
        assertNotNull(body);
        for (Object item : body) {
            assertInstanceOf(String.class, item, "Each country must be a string");
        }
    }

    // ---------------------------------------------------------------
    // GET /api/review
    // ---------------------------------------------------------------

    @Test
    void getReview_returns_paginated() throws Exception {
        HttpResponse<String> response = get("/api/review");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertTrue(body.containsKey("items"), "Response must contain 'items'");
        assertTrue(body.containsKey("total"), "Response must contain 'total'");
        assertTrue(body.containsKey("page"), "Response must contain 'page'");
        assertTrue(body.containsKey("size"), "Response must contain 'size'");
        assertInstanceOf(List.class, body.get("items"));
        assertInstanceOf(Number.class, body.get("total"));
        assertInstanceOf(Number.class, body.get("page"));
        assertInstanceOf(Number.class, body.get("size"));
    }

    // ---------------------------------------------------------------
    // POST /api/review/merge
    // ---------------------------------------------------------------

    @Test
    void mergeBooks_missingBody_returns400() throws Exception {
        // Send a body that is missing keep_id and merge_ids
        HttpResponse<String> response = post("/api/review/merge", "{}");
        assertEquals(400, response.statusCode());
    }

    // ---------------------------------------------------------------
    // POST /api/sync (auth-only check)
    // ---------------------------------------------------------------

    @Test
    void sync_withAuth_returns200() throws Exception {
        HttpResponse<String> response = post("/api/sync", "{}");
        assertEquals(200, response.statusCode());

        Map<String, Object> body = parseJson(response.body());
        assertTrue(body.containsKey("new_messages"), "Response must contain 'new_messages'");
        assertTrue(body.containsKey("books_found"), "Response must contain 'books_found'");
        assertTrue(body.containsKey("quotes_found"), "Response must contain 'quotes_found'");
        assertTrue(body.containsKey("photos_processed"), "Response must contain 'photos_processed'");
        assertTrue(body.containsKey("flagged_items"), "Response must contain 'flagged_items'");
        assertTrue(body.containsKey("duration_seconds"), "Response must contain 'duration_seconds'");
    }

    // ---------------------------------------------------------------
    // PUT /api/review/{id}/approve and dismiss (auth-only)
    // ---------------------------------------------------------------

    @Test
    void approve_withAuth_reachesHandler() throws Exception {
        // No real data to approve, but we verify auth passes and the handler is reached
        // The handler will try to parse body and update a non-existent row, which is fine (no error on 0-row update)
        HttpResponse<String> response = put("/api/review/99999/approve", "{\"corrected_text\":\"test\"}");
        // Should get 200 since ReviewDao.approve does an UPDATE that affects 0 rows but does not error
        assertEquals(200, response.statusCode());
    }

    @Test
    void dismiss_withAuth_reachesHandler() throws Exception {
        HttpResponse<String> response = put("/api/review/99999/dismiss", "{}");
        assertEquals(200, response.statusCode());
    }
}
