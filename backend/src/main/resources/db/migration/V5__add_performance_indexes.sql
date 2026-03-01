-- V5: Add missing indexes for frequently queried columns

-- Books page: ORDER BY announcement_date (default sort)
CREATE INDEX idx_book_announcement_date ON book(announcement_date DESC);

-- Quotes page: ORDER BY telegram_message_date DESC NULLS LAST
CREATE INDEX idx_quote_telegram_message_date ON quote(telegram_message_date DESC NULLS LAST);

-- book_author reverse lookup: finding books by author_id
-- (book_id, author_id) PK covers forward lookups; this covers reverse)
CREATE INDEX idx_book_author_author_id ON book_author(author_id);

-- Author name lookup: LOWER(name) = LOWER(?) used in insertOrFind and filters
CREATE INDEX idx_author_name_lower ON author(LOWER(name));
