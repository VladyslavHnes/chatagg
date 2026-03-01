# Data Model: Telegram Book & Quote Aggregator

**Branch**: `001-telegram-book-aggregator`
**Date**: 2026-02-16

## Entity Relationship Overview

```
sync_state (singleton)

telegram_message ─┬── book ──── book_author ──── author
                  └── quote ─── photo
```

- A `telegram_message` may produce a `book` (if it's a book
  announcement) or a `quote` (if it's a quote or photo).
- A `book` has many `author`s via `book_author` join table.
- A `quote` belongs to one `book` and optionally one `photo`.
- `sync_state` tracks incremental sync position.

## Tables

### book

| Column             | Type         | Constraints                  | Notes                                    |
|--------------------|--------------|------------------------------|------------------------------------------|
| id                 | BIGSERIAL    | PRIMARY KEY                  |                                          |
| title              | TEXT         | NOT NULL                     |                                          |
| review_note        | TEXT         |                              | Commentary from announcement message     |
| genre              | TEXT         |                              | From Open Library subjects               |
| announcement_date  | TIMESTAMPTZ  | NOT NULL                     | When book title/author was posted        |
| first_quote_date   | TIMESTAMPTZ  |                              | Earliest associated quote date           |
| reading_duration_days | INTEGER   |                              | Computed: announcement - first_quote     |
| telegram_message_id | BIGINT      | NOT NULL UNIQUE              | The announcement message ID              |
| cover_photo_path   | TEXT         |                              | Local path if cover photo posted         |
| metadata_source    | TEXT         |                              | 'openlibrary', 'wikidata', 'manual'      |
| created_at         | TIMESTAMPTZ  | NOT NULL DEFAULT now()       |                                          |
| updated_at         | TIMESTAMPTZ  | NOT NULL DEFAULT now()       |                                          |

### author

| Column         | Type       | Constraints             | Notes                          |
|----------------|------------|-------------------------|--------------------------------|
| id             | BIGSERIAL  | PRIMARY KEY             |                                |
| name           | TEXT       | NOT NULL                |                                |
| country        | TEXT       |                         | From Wikidata P27              |
| wikidata_id    | TEXT       |                         | e.g., Q34660                   |
| openlibrary_id | TEXT       |                         | e.g., OL23919A                 |
| created_at     | TIMESTAMPTZ | NOT NULL DEFAULT now() |                                |

### book_author

| Column    | Type   | Constraints                          |
|-----------|--------|--------------------------------------|
| book_id   | BIGINT | NOT NULL, FK -> book(id) ON DELETE CASCADE |
| author_id | BIGINT | NOT NULL, FK -> author(id) ON DELETE CASCADE |
|           |        | PRIMARY KEY (book_id, author_id)     |

### quote

| Column               | Type        | Constraints                    | Notes                              |
|----------------------|-------------|--------------------------------|------------------------------------|
| id                   | BIGSERIAL   | PRIMARY KEY                    |                                    |
| book_id              | BIGINT      | FK -> book(id) ON DELETE SET NULL |                                  |
| text_content         | TEXT        | NOT NULL                       |                                    |
| source_type          | TEXT        | NOT NULL                       | 'text' or 'photo'                  |
| telegram_message_id  | BIGINT      | NOT NULL UNIQUE                |                                    |
| telegram_message_date | TIMESTAMPTZ | NOT NULL                      |                                    |
| ocr_confidence       | REAL        |                                | null for text quotes               |
| photo_id             | BIGINT      | FK -> photo(id)                | null for text quotes               |
| review_status        | TEXT        | NOT NULL DEFAULT 'approved'    | 'approved', 'flagged', 'dismissed' |
| search_vector        | TSVECTOR    |                                | For full-text search               |
| created_at           | TIMESTAMPTZ | NOT NULL DEFAULT now()         |                                    |

**Indexes**:
- `GIN(search_vector)` for full-text search
- `(book_id)` for quote-by-book lookups
- `(review_status)` for review queue filtering

### photo

| Column           | Type        | Constraints              | Notes                     |
|------------------|-------------|--------------------------|---------------------------|
| id               | BIGSERIAL   | PRIMARY KEY              |                           |
| telegram_file_id | TEXT        | NOT NULL                 | Telegram's file reference |
| local_path       | TEXT        |                          | Path in photo volume      |
| ocr_text         | TEXT        |                          |                           |
| ocr_confidence   | REAL        |                          | Percentage 0-100          |
| created_at       | TIMESTAMPTZ | NOT NULL DEFAULT now()   |                           |

### telegram_message

| Column            | Type        | Constraints              | Notes                               |
|-------------------|-------------|--------------------------|---------------------------------------|
| id                | BIGSERIAL   | PRIMARY KEY              |                                       |
| telegram_message_id | BIGINT    | NOT NULL UNIQUE          |                                       |
| chat_id           | BIGINT      | NOT NULL                 |                                       |
| message_date      | TIMESTAMPTZ | NOT NULL                 |                                       |
| message_type      | TEXT        | NOT NULL                 | 'text', 'photo', 'other'             |
| raw_text          | TEXT        |                          | Original message text                 |
| processing_status | TEXT        | NOT NULL DEFAULT 'pending' | 'pending','processed','skipped','flagged' |
| processed_at      | TIMESTAMPTZ |                          |                                       |

**Indexes**:
- `(telegram_message_id)` UNIQUE for dedup
- `(processing_status)` for finding unprocessed messages
- `(message_date)` for chronological ordering

### sync_state

| Column                  | Type        | Constraints              |
|-------------------------|-------------|--------------------------|
| id                      | BIGSERIAL   | PRIMARY KEY              |
| channel_chat_id         | BIGINT      | NOT NULL                 |
| last_message_id         | BIGINT      |                          |
| last_sync_at            | TIMESTAMPTZ |                          |
| total_messages_processed | BIGINT     | NOT NULL DEFAULT 0       |

## State Transitions

### telegram_message.processing_status

```
pending → processed   (successfully parsed as book or quote)
pending → skipped     (not a book or quote, e.g., casual comment)
pending → flagged     (ambiguous content, needs manual review)
flagged → processed   (resolved via review queue)
flagged → skipped     (dismissed via review queue)
```

### quote.review_status

```
approved              (text quote or high-confidence OCR)
flagged               (low OCR confidence or unresolved book link)
flagged → approved    (corrected via review queue)
flagged → dismissed   (discarded via review queue)
```

## Validation Rules

- `book.title` MUST NOT be empty.
- `book.telegram_message_id` MUST be unique (no duplicate books from
  same message).
- `quote.text_content` MUST NOT be empty.
- `quote.source_type` MUST be one of: 'text', 'photo'.
- `quote.review_status` MUST be one of: 'approved', 'flagged',
  'dismissed'.
- `telegram_message.processing_status` MUST be one of: 'pending',
  'processed', 'skipped', 'flagged'.
- `book.reading_duration_days` is computed as
  `announcement_date - first_quote_date` in days. If `first_quote_date`
  is null, reading duration is null (single-session book).

## Full-Text Search Strategy

```sql
-- Trigger to auto-update search_vector on quote insert/update
CREATE OR REPLACE FUNCTION update_quote_search_vector()
RETURNS TRIGGER AS $$
BEGIN
  NEW.search_vector := to_tsvector('simple', NEW.text_content);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Search query
SELECT q.*, b.title AS book_title
FROM quote q
JOIN book b ON q.book_id = b.id
WHERE q.search_vector @@ plainto_tsquery('simple', :query)
  AND q.review_status = 'approved'
ORDER BY ts_rank(q.search_vector, plainto_tsquery('simple', :query)) DESC;
```

Using `'simple'` configuration (no stemming) to support both Ukrainian
and English text without language detection. This provides token-based
matching which is sufficient for quote search.
