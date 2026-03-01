package com.chatagg.api;

import com.chatagg.db.AuthorDao;
import com.chatagg.db.BookDao;
import com.chatagg.db.DatabaseManager;
import com.chatagg.model.Author;
import com.chatagg.model.Book;
import io.javalin.http.Context;

import java.sql.*;
import java.util.*;

public class BookController {

    private final BookDao bookDao;
    private final AuthorDao authorDao;
    private final DatabaseManager db;
    private final ResponseCache cache;

    public BookController(DatabaseManager db, ResponseCache cache) {
        this.db = db;
        this.bookDao = new BookDao(db);
        this.authorDao = new AuthorDao(db);
        this.cache = cache;
    }

    public void listBooks(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
        String sort = ctx.queryParamAsClass("sort", String.class).getOrDefault("date_desc");
        String genre = ctx.queryParam("genre");
        String country = ctx.queryParam("country");
        String title = ctx.queryParam("title");
        String author = ctx.queryParam("author");

        if (genre != null && genre.isEmpty()) genre = null;
        if (country != null && country.isEmpty()) country = null;
        if (title != null && title.isEmpty()) title = null;
        if (author != null && author.isEmpty()) author = null;

        String cacheKey = "books:" + ctx.queryString();
        Object cached = cache.get(cacheKey);
        if (cached != null) { ctx.json(cached); return; }

        Map<String, Object> result = bookDao.findAll(page, size, sort, genre, country, title, author);
        cache.put(cacheKey, result);
        ctx.json(result);
    }

    public void createBook(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            ctx.status(400).json(Map.of("error", "Title is required"));
            return;
        }
        String genre = body.get("genre");
        String authorName = body.get("author");
        String authorCountry = body.get("author_country");
        String date = body.get("date");

        long bookId = bookDao.insertManual(title.trim(),
                genre != null && !genre.isBlank() ? genre.trim() : null,
                date != null && !date.isBlank() ? date.trim() : null);

        if (authorName != null && !authorName.isBlank()) {
            long authorId = authorDao.insertOrFind(authorName.trim(),
                    authorCountry != null && !authorCountry.isBlank() ? authorCountry.trim() : null);
            authorDao.linkToBook(bookId, authorId);
        }

        cache.invalidateAll();
        ctx.json(Map.of("id", bookId));
    }

    public void getBook(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Book book = bookDao.findById(id);

        if (book == null) {
            ctx.status(404).result("Book not found");
            return;
        }

        List<Author> authors = authorDao.findByBookId(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", book.getId());
        detail.put("title", book.getTitle());
        detail.put("authors", authors.stream().map(a -> {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", a.getId());
            am.put("name", a.getName());
            am.put("country", a.getCountry());
            return am;
        }).toList());
        detail.put("genre", book.getGenre());
        detail.put("review_note", book.getReviewNote());
        detail.put("impression", book.getImpression());
        detail.put("announcement_date", book.getAnnouncementDate() != null ? book.getAnnouncementDate().toString() : null);
        detail.put("cover_photo_path", book.getCoverPhotoPath());
        detail.put("metadata_source", book.getMetadataSource());
        detail.put("telegram_message_id", book.getTelegramMessageId());
        detail.put("quotes", List.of()); // Populated by QuoteController on /api/books/{id}/quotes

        ctx.json(detail);
    }

    public void getNeighbors(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        BookDao.BookNeighbors neighbors = bookDao.findNeighbors(id);

        Map<String, Object> result = new LinkedHashMap<>();
        if (neighbors.prevId() != null) {
            result.put("prev", Map.of("id", neighbors.prevId(), "title", neighbors.prevTitle()));
        } else {
            result.put("prev", null);
        }
        if (neighbors.nextId() != null) {
            result.put("next", Map.of("id", neighbors.nextId(), "title", neighbors.nextTitle()));
        } else {
            result.put("next", null);
        }
        ctx.json(result);
    }

    public void deleteBook(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Book book = bookDao.findById(id);
        if (book == null) {
            ctx.status(404).json(Map.of("error", "Book not found"));
            return;
        }

        // Find neighbor to reassign quotes to
        BookDao.BookNeighbors neighbors = bookDao.findNeighbors(id);
        Long targetId = neighbors.nextId() != null ? neighbors.nextId() : neighbors.prevId();

        // Reassign quotes if there's a neighbor
        if (targetId != null) {
            var quotes = new com.chatagg.db.QuoteDao(db).findByBookId(id);
            var quoteDao = new com.chatagg.db.QuoteDao(db);
            for (var q : quotes) {
                quoteDao.updateBookId(q.getId(), targetId);
            }
        }

        bookDao.delete(id);
        cache.invalidateAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("quotes_moved_to", targetId);
        ctx.json(result);
    }

    public void getAuthor(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Author author = authorDao.findById(id);
        if (author == null) {
            ctx.status(404).result("Author not found");
            return;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", author.getId());
        result.put("name", author.getName());
        result.put("country", author.getCountry());
        result.put("books", bookDao.findByAuthorId(id));
        ctx.json(result);
    }

    public void addBookToAuthor(Context ctx) {
        long authorId = ctx.pathParamAsClass("id", Long.class).get();
        Author author = authorDao.findById(authorId);
        if (author == null) {
            ctx.status(404).result("Author not found");
            return;
        }
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            ctx.status(400).json(Map.of("error", "Title is required"));
            return;
        }
        String genre = body.get("genre");
        long bookId = bookDao.insertManual(title.trim(), genre != null && !genre.isBlank() ? genre.trim() : null, null);
        authorDao.linkToBook(bookId, authorId);
        ctx.json(Map.of("id", bookId));
    }

    public void listAuthors(Context ctx) {
        Object cached = cache.get("authors");
        if (cached != null) { ctx.json(cached); return; }
        List<Map<String, Object>> result = authorDao.findAll().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("country", a.getCountry());
            return m;
        }).toList();
        cache.put("authors", result);
        ctx.json(result);
    }

    @SuppressWarnings("unchecked")
    public void mergeAuthors(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || !body.containsKey("keep_id") || !body.containsKey("merge_ids")) {
            ctx.status(400).result("Missing keep_id or merge_ids");
            return;
        }
        long keepId = ((Number) body.get("keep_id")).longValue();
        List<Number> mergeIdsRaw = (List<Number>) body.get("merge_ids");
        List<Long> mergeIds = mergeIdsRaw.stream().map(Number::longValue).toList();
        if (mergeIds.isEmpty()) {
            ctx.status(400).result("merge_ids must not be empty");
            return;
        }
        authorDao.mergeAuthors(keepId, mergeIds);
        cache.invalidateAll();
        ctx.json(Map.of("status", "merged"));
    }

    public void deleteAuthor(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        authorDao.delete(id);
        cache.invalidateAll();
        ctx.json(Map.of("deleted", true));
    }

    public void editAuthor(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String name = body.get("name");
        String country = body.get("country");

        if (name == null || name.isBlank()) {
            ctx.status(400).json(Map.of("error", "Name is required"));
            return;
        }

        authorDao.updateAuthor(id, name.trim(), country != null ? country.trim() : null);
        cache.invalidateAll();
        ctx.json(Map.of("ok", true));
    }

    public void updateBookTitle(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            ctx.status(400).json(Map.of("error", "Title is required"));
            return;
        }
        bookDao.updateTitle(id, title.trim());
        cache.invalidateAll();
        ctx.json(Map.of("ok", true));
    }

    public void updateBookImpression(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String impression = body.get("impression");
        bookDao.updateImpression(id, impression);
        ctx.json(Map.of("ok", true));
    }

    public void updateBookGenre(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String genre = body.get("genre");
        bookDao.updateGenre(id, genre != null && !genre.isBlank() ? genre.trim() : null, "manual");
        cache.invalidateAll();
        ctx.json(Map.of("ok", true));
    }

    public void listGenres(Context ctx) {
        Object cached = cache.get("genres");
        if (cached != null) { ctx.json(cached); return; }
        List<String> genres = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT DISTINCT genre FROM book WHERE genre IS NOT NULL ORDER BY genre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                genres.add(rs.getString("genre"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        cache.put("genres", genres);
        ctx.json(genres);
    }

    public void listCountries(Context ctx) {
        Object cached = cache.get("countries");
        if (cached != null) { ctx.json(cached); return; }
        List<String> countries = authorDao.findAllCountries();
        cache.put("countries", countries);
        ctx.json(countries);
    }
}
