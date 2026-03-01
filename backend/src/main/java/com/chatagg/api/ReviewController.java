package com.chatagg.api;

import com.chatagg.db.DatabaseManager;
import com.chatagg.db.ReviewDao;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class ReviewController {

    private final ReviewDao reviewDao;

    public ReviewController(DatabaseManager db) {
        this.reviewDao = new ReviewDao(db);
    }

    public void listFlagged(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
        String type = ctx.queryParamAsClass("type", String.class).getOrDefault("all");

        Map<String, Object> result = reviewDao.findFlagged(page, size, type);
        ctx.json(result);
    }

    public void approve(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String correctedText = body != null ? (String) body.get("corrected_text") : null;
        Long bookId = null;
        if (body != null && body.get("book_id") != null) {
            bookId = ((Number) body.get("book_id")).longValue();
        }

        reviewDao.approve(id, correctedText, bookId);
        ctx.json(Map.of("status", "approved"));
    }

    public void dismiss(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        reviewDao.dismiss(id);
        ctx.json(Map.of("status", "dismissed"));
    }

    public void mergeBooks(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || !body.containsKey("keep_id") || !body.containsKey("merge_ids")) {
            ctx.status(400).result("Missing keep_id or merge_ids");
            return;
        }

        long keepId = ((Number) body.get("keep_id")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> mergeIdsRaw = (List<Number>) body.get("merge_ids");
        List<Long> mergeIds = mergeIdsRaw.stream().map(Number::longValue).toList();

        if (mergeIds.isEmpty()) {
            ctx.status(400).result("merge_ids must not be empty");
            return;
        }

        reviewDao.mergeBooks(keepId, mergeIds);
        ctx.json(Map.of("status", "merged"));
    }
}
