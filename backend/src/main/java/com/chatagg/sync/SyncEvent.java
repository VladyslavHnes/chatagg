package com.chatagg.sync;

public sealed interface SyncEvent permits
        SyncEvent.Connecting,
        SyncEvent.Fetched,
        SyncEvent.BookFound,
        SyncEvent.QuoteFound,
        SyncEvent.PhotoProcessed,
        SyncEvent.Flagged,
        SyncEvent.EnrichingAuthors,
        SyncEvent.EnrichingBooks,
        SyncEvent.Done,
        SyncEvent.Error,
        SyncEvent.AuthRequired {

    String type();
    String toJson();

    record Connecting() implements SyncEvent {
        public String type() { return "connecting"; }
        public String toJson() { return "{\"type\":\"connecting\"}"; }
    }

    record Fetched(int count) implements SyncEvent {
        public String type() { return "fetched"; }
        public String toJson() { return "{\"type\":\"fetched\",\"count\":" + count + "}"; }
    }

    record BookFound(String title, String author) implements SyncEvent {
        public String type() { return "book_found"; }
        public String toJson() {
            return "{\"type\":\"book_found\",\"title\":" + jsonStr(title) + ",\"author\":" + jsonStr(author) + "}";
        }
    }

    record QuoteFound(String snippet) implements SyncEvent {
        public String type() { return "quote_found"; }
        public String toJson() {
            return "{\"type\":\"quote_found\",\"snippet\":" + jsonStr(snippet) + "}";
        }
    }

    record PhotoProcessed() implements SyncEvent {
        public String type() { return "photo_processed"; }
        public String toJson() { return "{\"type\":\"photo_processed\"}"; }
    }

    record Flagged(String reason) implements SyncEvent {
        public String type() { return "flagged"; }
        public String toJson() {
            return "{\"type\":\"flagged\",\"reason\":" + jsonStr(reason) + "}";
        }
    }

    record EnrichingAuthors(int count) implements SyncEvent {
        public String type() { return "enriching_authors"; }
        public String toJson() { return "{\"type\":\"enriching_authors\",\"count\":" + count + "}"; }
    }

    record EnrichingBooks(int count) implements SyncEvent {
        public String type() { return "enriching_books"; }
        public String toJson() { return "{\"type\":\"enriching_books\",\"count\":" + count + "}"; }
    }

    record Done(int newMessages, int booksFound, int quotesFound, int photosProcessed,
                int flaggedItems, double durationSeconds) implements SyncEvent {
        public String type() { return "done"; }
        public String toJson() {
            return "{\"type\":\"done\""
                    + ",\"new_messages\":" + newMessages
                    + ",\"books_found\":" + booksFound
                    + ",\"quotes_found\":" + quotesFound
                    + ",\"photos_processed\":" + photosProcessed
                    + ",\"flagged_items\":" + flaggedItems
                    + ",\"duration_seconds\":" + String.format("%.2f", durationSeconds)
                    + "}";
        }
    }

    record Error(String message) implements SyncEvent {
        public String type() { return "error"; }
        public String toJson() {
            return "{\"type\":\"error\",\"message\":" + jsonStr(message) + "}";
        }
    }

    record AuthRequired(String authType) implements SyncEvent {
        public String type() { return "auth_required"; }
        public String toJson() {
            return "{\"type\":\"auth_required\",\"auth_type\":" + jsonStr(authType) + "}";
        }
    }

    static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }
}
