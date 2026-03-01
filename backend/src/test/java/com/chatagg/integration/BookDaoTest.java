package com.chatagg.integration;

import com.chatagg.db.AuthorDao;
import com.chatagg.db.BookDao;
import com.chatagg.db.DatabaseManager;
import com.chatagg.model.Book;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class BookDaoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static DatabaseManager db;
    static BookDao bookDao;
    static AuthorDao authorDao;

    @BeforeAll
    static void setup() {
        db = TestDatabaseHelper.dbFrom(postgres);
        bookDao = new BookDao(db);
        authorDao = new AuthorDao(db);
    }

    @AfterAll
    static void teardown() {
        if (db != null) db.close();
    }

    @Test
    void insertAndFindById() {
        Book book = new Book();
        book.setTitle("Test Book");
        book.setAnnouncementDate(Instant.parse("2026-01-15T12:00:00Z"));
        book.setTelegramMessageId(1001L);

        long id = bookDao.insert(book);
        assertTrue(id > 0);

        Book found = bookDao.findById(id);
        assertNotNull(found);
        assertEquals("Test Book", found.getTitle());
        assertEquals(1001L, found.getTelegramMessageId());
    }

    @Test
    void findAllPaginated_dateDesc() {
        for (int i = 0; i < 5; i++) {
            Book book = new Book();
            book.setTitle("Paginated Book " + i);
            book.setAnnouncementDate(Instant.parse("2026-02-0" + (i + 1) + "T12:00:00Z"));
            book.setTelegramMessageId(2000L + i);
            bookDao.insert(book);
        }

        Map<String, Object> result = bookDao.findAll(1, 3, "date_desc", null, null);
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertTrue(items.size() <= 3);
        assertTrue((int) result.get("total") >= 5);
    }

    @Test
    void findAllPaginated_titleAsc() {
        Map<String, Object> result = bookDao.findAll(1, 100, "title_asc", null, null);
        assertNotNull(result);
    }

    @Test
    void findByTelegramMessageId_found() {
        Book book = new Book();
        book.setTitle("Unique TG Book");
        book.setAnnouncementDate(Instant.now());
        book.setTelegramMessageId(3001L);
        bookDao.insert(book);

        Book found = bookDao.findByTelegramMessageId(3001L);
        assertNotNull(found);
        assertEquals("Unique TG Book", found.getTitle());
    }

    @Test
    void findByTelegramMessageId_notFound() {
        Book found = bookDao.findByTelegramMessageId(999999L);
        assertNull(found);
    }

    @Test
    void duplicateDetection() {
        Book book = new Book();
        book.setTitle("Duplicate Book");
        book.setAnnouncementDate(Instant.now());
        book.setTelegramMessageId(4001L);
        bookDao.insert(book);

        List<Book> duplicates = bookDao.findPotentialDuplicates("Duplicate Book", "Some Author");
        assertFalse(duplicates.isEmpty());
    }
}
