package com.chatagg.api;

import com.chatagg.db.DatabaseManager;
import com.chatagg.db.PhotoDao;
import com.chatagg.db.QuoteDao;
import com.chatagg.model.Photo;
import com.chatagg.model.Quote;
import io.javalin.http.Context;

import java.util.*;

public class QuoteController {

    private final QuoteDao quoteDao;
    private final PhotoDao photoDao;

    public QuoteController(DatabaseManager db) {
        this.quoteDao = new QuoteDao(db);
        this.photoDao = new PhotoDao(db);
    }

    public void search(Context ctx) {
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.json(Map.of("items", List.of(), "total", 0, "page", 1, "size", 20, "query", ""));
            return;
        }

        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);

        Map<String, Object> result = quoteDao.searchPaginated(query, page, size);
        ctx.json(result);
    }

    public void listByBook(Context ctx) {
        long bookId = ctx.pathParamAsClass("id", Long.class).get();
        List<Quote> quotes = quoteDao.findByBookId(bookId);

        List<Map<String, Object>> items = quotes.stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("text_content", q.getTextContent());
            m.put("source_type", q.getSourceType());
            m.put("telegram_message_date", q.getTelegramMessageDate() != null ? q.getTelegramMessageDate().toString() : null);
            m.put("ocr_confidence", q.getOcrConfidence());
            m.put("photo_id", q.getPhotoId());
            return m;
        }).toList();

        ctx.json(items);
    }

    public void servePhoto(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Photo photo = photoDao.findById(id);
        if (photo == null || photo.getLocalPath() == null) {
            ctx.status(404).result("Photo not found");
            return;
        }
        java.nio.file.Path path = java.nio.file.Path.of(photo.getLocalPath());
        if (!java.nio.file.Files.exists(path)) {
            ctx.status(404).result("Photo file not found");
            return;
        }
        ctx.contentType("image/jpeg");
        try {
            ctx.result(java.nio.file.Files.newInputStream(path));
        } catch (java.io.IOException e) {
            ctx.status(500).result("Failed to read photo");
        }
    }

    public void getQuote(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, Object> quote = quoteDao.findByIdWithBook(id);
        if (quote == null) {
            ctx.status(404).json(Map.of("error", "Quote not found"));
            return;
        }
        ctx.json(quote);
    }

    public void browseQuotes(Context ctx) {
        String query = ctx.queryParam("q");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
        ctx.json(quoteDao.browseApproved(query, page, size));
    }

    public void randomQuote(Context ctx) {
        Map<String, Object> quote = quoteDao.findRandom();
        if (quote == null) {
            ctx.status(404).json(Map.of("error", "No quotes available"));
            return;
        }
        // Don't send OCR text for photo quotes
        if (quote.get("photo_id") != null) {
            quote.remove("text_content");
        }
        ctx.json(quote);
    }

    public void moveQuote(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        MoveRequest body = ctx.bodyAsClass(MoveRequest.class);
        if (body.book_id() == null) {
            ctx.status(400).json(Map.of("error", "book_id is required"));
            return;
        }
        quoteDao.updateBookId(id, body.book_id());
        ctx.json(Map.of("moved", true));
    }

    public void editQuote(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        EditRequest body = ctx.bodyAsClass(EditRequest.class);
        if (body.text_content() == null || body.text_content().isBlank()) {
            ctx.status(400).json(Map.of("error", "text_content is required"));
            return;
        }
        quoteDao.updateTextContent(id, body.text_content());
        ctx.json(Map.of("updated", true));
    }

    public void deleteQuote(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        quoteDao.delete(id);
        ctx.json(Map.of("deleted", true));
    }

    public void unlinkQuote(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        quoteDao.unlinkFromBook(id);
        ctx.json(Map.of("unlinked", true));
    }

    private record MoveRequest(Long book_id) {}
    private record EditRequest(String text_content) {}
}
