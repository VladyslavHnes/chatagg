-- V2: Add full-text search support for quotes

ALTER TABLE quote ADD COLUMN search_vector TSVECTOR;

CREATE INDEX idx_quote_search_vector ON quote USING GIN(search_vector);

CREATE OR REPLACE FUNCTION update_quote_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', NEW.text_content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_quote_search_vector
    BEFORE INSERT OR UPDATE OF text_content ON quote
    FOR EACH ROW
    EXECUTE FUNCTION update_quote_search_vector();
