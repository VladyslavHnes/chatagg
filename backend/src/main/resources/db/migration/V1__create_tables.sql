-- V1: Create all core tables for Chatagg

CREATE TABLE book (
    id                    BIGSERIAL    PRIMARY KEY,
    title                 TEXT         NOT NULL,
    review_note           TEXT,
    genre                 TEXT,
    announcement_date     TIMESTAMPTZ  NOT NULL,
    first_quote_date      TIMESTAMPTZ,
    reading_duration_days INTEGER,
    telegram_message_id   BIGINT       NOT NULL UNIQUE,
    cover_photo_path      TEXT,
    metadata_source       TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE author (
    id             BIGSERIAL    PRIMARY KEY,
    name           TEXT         NOT NULL,
    country        TEXT,
    wikidata_id    TEXT,
    openlibrary_id TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE book_author (
    book_id   BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES author(id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE photo (
    id               BIGSERIAL    PRIMARY KEY,
    telegram_file_id TEXT         NOT NULL,
    local_path       TEXT,
    ocr_text         TEXT,
    ocr_confidence   REAL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE quote (
    id                    BIGSERIAL    PRIMARY KEY,
    book_id               BIGINT       REFERENCES book(id) ON DELETE SET NULL,
    text_content          TEXT         NOT NULL,
    source_type           TEXT         NOT NULL,
    telegram_message_id   BIGINT       NOT NULL UNIQUE,
    telegram_message_date TIMESTAMPTZ  NOT NULL,
    ocr_confidence        REAL,
    photo_id              BIGINT       REFERENCES photo(id),
    review_status         TEXT         NOT NULL DEFAULT 'approved',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_quote_book_id ON quote(book_id);
CREATE INDEX idx_quote_review_status ON quote(review_status);

CREATE TABLE telegram_message (
    id                  BIGSERIAL    PRIMARY KEY,
    telegram_message_id BIGINT       NOT NULL UNIQUE,
    chat_id             BIGINT       NOT NULL,
    message_date        TIMESTAMPTZ  NOT NULL,
    message_type        TEXT         NOT NULL,
    raw_text            TEXT,
    processing_status   TEXT         NOT NULL DEFAULT 'pending',
    processed_at        TIMESTAMPTZ
);

CREATE INDEX idx_telegram_message_status ON telegram_message(processing_status);
CREATE INDEX idx_telegram_message_date ON telegram_message(message_date);

CREATE TABLE sync_state (
    id                       BIGSERIAL    PRIMARY KEY,
    channel_chat_id          BIGINT       NOT NULL,
    last_message_id          BIGINT,
    last_sync_at             TIMESTAMPTZ,
    total_messages_processed BIGINT       NOT NULL DEFAULT 0
);
