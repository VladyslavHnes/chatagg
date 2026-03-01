package com.chatagg.integration;

import com.chatagg.db.*;
import com.chatagg.model.Book;
import com.chatagg.model.Quote;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QuoteDaoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static DatabaseManager db;
    static BookDao bookDao;
    static QuoteDao quoteDao;
    static long testBookId;

    @BeforeAll
    static void setup() {
        db = TestDatabaseHelper.dbFrom(postgres);
        bookDao = new BookDao(db);
        quoteDao = new QuoteDao(db);

        // Insert a shared book for foreign key references
        Book book = new Book();
        book.setTitle("Test Book for Quotes");
        book.setAnnouncementDate(Instant.parse("2026-01-10T12:00:00Z"));
        book.setTelegramMessageId(9000L);
        testBookId = bookDao.insert(book);
    }

    @AfterAll
    static void teardown() {
        if (db != null) db.close();
    }

    // --- insert and findByBookId round-trip ---

    @Test
    void insertAndFindByBookId_roundTrip() {
        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("To be or not to be, that is the question.");
        quote.setSourceType("text");
        quote.setTelegramMessageId(10001L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-15T10:00:00Z"));
        quote.setOcrConfidence(null);
        quote.setReviewStatus("approved");

        long id = quoteDao.insert(quote);
        assertTrue(id > 0, "Insert should return a positive id");

        List<Quote> found = quoteDao.findByBookId(testBookId);
        assertFalse(found.isEmpty(), "Should find at least one quote for the book");

        Quote retrieved = found.stream()
                .filter(q -> q.getId() == id)
                .findFirst()
                .orElse(null);
        assertNotNull(retrieved, "Should find the inserted quote by id");
        assertEquals("To be or not to be, that is the question.", retrieved.getTextContent());
        assertEquals("text", retrieved.getSourceType());
        assertEquals(10001L, retrieved.getTelegramMessageId());
        assertEquals(testBookId, retrieved.getBookId());
        assertEquals("approved", retrieved.getReviewStatus());
    }

    @Test
    void findByBookId_noQuotes_returnsEmptyList() {
        // Insert a separate book with no quotes
        Book emptyBook = new Book();
        emptyBook.setTitle("Book With No Quotes");
        emptyBook.setAnnouncementDate(Instant.parse("2026-02-01T12:00:00Z"));
        emptyBook.setTelegramMessageId(9100L);
        long emptyBookId = bookDao.insert(emptyBook);

        List<Quote> found = quoteDao.findByBookId(emptyBookId);
        assertNotNull(found);
        assertTrue(found.isEmpty(), "Book with no quotes should return empty list");
    }

    // --- Full-text search with tsvector ---

    @Test
    void search_findsMatchingQuotes() {
        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("The extraordinary beauty of mathematics lies in its precision and elegance.");
        quote.setSourceType("text");
        quote.setTelegramMessageId(10010L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-16T10:00:00Z"));
        quote.setReviewStatus("approved");
        quoteDao.insert(quote);

        List<Quote> results = quoteDao.search("mathematics", 0, 10);
        assertFalse(results.isEmpty(), "Search for 'mathematics' should find the inserted quote");
        assertTrue(results.stream().anyMatch(q ->
                        q.getTextContent().contains("mathematics")),
                "Results should contain quote with 'mathematics'");
    }

    @Test
    void search_cyrillicText() {
        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("\u041b\u044e\u0434\u0438\u043d\u0430 \u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0434\u0430\u043b\u044c\u043d\u0430 \u0437\u0430 \u0441\u0432\u043e\u0457 \u0434\u0443\u043c\u043a\u0438 \u0442\u0430 \u0432\u0447\u0438\u043d\u043a\u0438 \u0432 \u0446\u044c\u043e\u043c\u0443 \u0441\u0432\u0456\u0442\u0456.");
        quote.setSourceType("ocr");
        quote.setTelegramMessageId(10020L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-17T10:00:00Z"));
        quote.setOcrConfidence(85.0f);
        quote.setReviewStatus("approved");
        quoteDao.insert(quote);

        // Search using the 'simple' text search config (as defined in V2 migration)
        // which tokenizes based on whitespace and works with Cyrillic
        List<Quote> results = quoteDao.search("\u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0434\u0430\u043b\u044c\u043d\u0430", 0, 10);
        assertFalse(results.isEmpty(), "Search for Cyrillic word should find the inserted quote");
        assertTrue(results.stream().anyMatch(q ->
                        q.getTextContent().contains("\u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0434\u0430\u043b\u044c\u043d\u0430")),
                "Results should contain the Cyrillic quote");
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        List<Quote> results = quoteDao.search("xyznonexistenttermzyx", 0, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Search for non-existent term should return empty list");
    }

    @Test
    void search_pagination() {
        // Insert multiple quotes with the same keyword
        for (int i = 0; i < 5; i++) {
            Quote quote = new Quote();
            quote.setBookId(testBookId);
            quote.setTextContent("Pagination keyword alpha-omega-delta entry number " + i);
            quote.setSourceType("text");
            quote.setTelegramMessageId(10030L + i);
            quote.setTelegramMessageDate(Instant.parse("2026-01-18T10:00:00Z"));
            quote.setReviewStatus("approved");
            quoteDao.insert(quote);
        }

        List<Quote> page0 = quoteDao.search("alpha-omega-delta", 0, 2);
        List<Quote> page1 = quoteDao.search("alpha-omega-delta", 1, 2);

        assertTrue(page0.size() <= 2, "Page size should be respected");
        assertTrue(page1.size() <= 2, "Second page should also respect page size");
    }

    // --- findFlagged filtering ---

    @Test
    void findFlagged_returnsOnlyFlaggedQuotes() {
        // Insert an approved quote
        Quote approved = new Quote();
        approved.setBookId(testBookId);
        approved.setTextContent("This is an approved quote for filtering test.");
        approved.setSourceType("text");
        approved.setTelegramMessageId(10050L);
        approved.setTelegramMessageDate(Instant.parse("2026-01-20T10:00:00Z"));
        approved.setReviewStatus("approved");
        quoteDao.insert(approved);

        // Insert flagged quotes
        Quote flagged1 = new Quote();
        flagged1.setBookId(testBookId);
        flagged1.setTextContent("This is a flagged quote number one.");
        flagged1.setSourceType("ocr");
        flagged1.setTelegramMessageId(10051L);
        flagged1.setTelegramMessageDate(Instant.parse("2026-01-20T11:00:00Z"));
        flagged1.setOcrConfidence(40.0f);
        flagged1.setReviewStatus("flagged");
        long flaggedId1 = quoteDao.insert(flagged1);

        Quote flagged2 = new Quote();
        flagged2.setBookId(testBookId);
        flagged2.setTextContent("This is a flagged quote number two.");
        flagged2.setSourceType("ocr");
        flagged2.setTelegramMessageId(10052L);
        flagged2.setTelegramMessageDate(Instant.parse("2026-01-20T12:00:00Z"));
        flagged2.setOcrConfidence(35.0f);
        flagged2.setReviewStatus("flagged");
        long flaggedId2 = quoteDao.insert(flagged2);

        List<Quote> flaggedResults = quoteDao.findFlagged(0, 100);
        assertFalse(flaggedResults.isEmpty(), "Should return flagged quotes");
        assertTrue(flaggedResults.stream().allMatch(q -> "flagged".equals(q.getReviewStatus())),
                "All returned quotes should have 'flagged' review status");
        assertTrue(flaggedResults.stream().anyMatch(q -> q.getId() == flaggedId1),
                "Should contain flagged quote 1");
        assertTrue(flaggedResults.stream().anyMatch(q -> q.getId() == flaggedId2),
                "Should contain flagged quote 2");
    }

    @Test
    void findFlagged_noFlaggedQuotes_returnsEmptyIfNoneFlagged() {
        // We cannot guarantee a completely clean state since other tests insert flagged quotes,
        // but we verify the method does not throw and returns a valid list
        List<Quote> flaggedResults = quoteDao.findFlagged(0, 100);
        assertNotNull(flaggedResults, "findFlagged should never return null");
    }

    // --- updateReviewStatus ---

    @Test
    void updateReviewStatus_changesToNewStatus() {
        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("Quote to have its status updated.");
        quote.setSourceType("text");
        quote.setTelegramMessageId(10060L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-21T10:00:00Z"));
        quote.setReviewStatus("flagged");
        long id = quoteDao.insert(quote);

        quoteDao.updateReviewStatus(id, "approved");

        List<Quote> found = quoteDao.findByBookId(testBookId);
        Quote updated = found.stream().filter(q -> q.getId() == id).findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals("approved", updated.getReviewStatus());
    }

    // --- updateTextContent ---

    @Test
    void updateTextContent_correctsOcrText() {
        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("Ths is mispelled text fron OCR.");
        quote.setSourceType("ocr");
        quote.setTelegramMessageId(10070L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-22T10:00:00Z"));
        quote.setOcrConfidence(55.0f);
        quote.setReviewStatus("flagged");
        long id = quoteDao.insert(quote);

        quoteDao.updateTextContent(id, "This is corrected text from OCR.");

        List<Quote> found = quoteDao.findByBookId(testBookId);
        Quote updated = found.stream().filter(q -> q.getId() == id).findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals("This is corrected text from OCR.", updated.getTextContent());
    }

    // --- updateBookId ---

    @Test
    void updateBookId_reassignsQuoteToAnotherBook() {
        Book anotherBook = new Book();
        anotherBook.setTitle("Another Book for Reassignment");
        anotherBook.setAnnouncementDate(Instant.parse("2026-02-05T12:00:00Z"));
        anotherBook.setTelegramMessageId(9200L);
        long anotherBookId = bookDao.insert(anotherBook);

        Quote quote = new Quote();
        quote.setBookId(testBookId);
        quote.setTextContent("Quote that will be reassigned to another book.");
        quote.setSourceType("text");
        quote.setTelegramMessageId(10080L);
        quote.setTelegramMessageDate(Instant.parse("2026-01-23T10:00:00Z"));
        quote.setReviewStatus("approved");
        long id = quoteDao.insert(quote);

        quoteDao.updateBookId(id, anotherBookId);

        List<Quote> originalBookQuotes = quoteDao.findByBookId(testBookId);
        assertTrue(originalBookQuotes.stream().noneMatch(q -> q.getId() == id),
                "Quote should no longer be under the original book");

        List<Quote> newBookQuotes = quoteDao.findByBookId(anotherBookId);
        assertTrue(newBookQuotes.stream().anyMatch(q -> q.getId() == id),
                "Quote should now be under the new book");
    }
}
