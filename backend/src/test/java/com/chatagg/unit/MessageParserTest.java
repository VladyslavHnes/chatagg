package com.chatagg.unit;

import com.chatagg.telegram.MessageParser;
import com.chatagg.telegram.MessageParser.BookEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageParserTest {

    private final MessageParser parser = new MessageParser();

    @Test
    void parseAuthorGuillemetsTitle_cyrillic() {
        String text = "Майґуль Аксельссон «Квітнева відьма»";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Квітнева відьма", entry.title());
        assertEquals("Майґуль Аксельссон", entry.author());
        assertNull(entry.reviewNote());
    }

    @Test
    void parseGuillemetsTitle_authorAfter() {
        String text = "«Червона Королева» Метт Рідлі";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Червона Королева", entry.title());
        assertEquals("Метт Рідлі", entry.author());
    }

    @Test
    void parseDoubleQuotesTitle_authorAfter() {
        String text = "\"The way of the superior man\" David Deida";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("The way of the superior man", entry.title());
        assertEquals("David Deida", entry.author());
    }

    @Test
    void parseAuthorDoubleQuotesTitle() {
        String text = "Walter J. Ong \"Orality and Literacy\"";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Orality and Literacy", entry.title());
        assertEquals("Walter J. Ong", entry.author());
    }

    @Test
    void parseWithReviewNote() {
        String text = "Автор «Назва книги»\nДуже гарна книга, рекомендую прочитати.\nМені сподобалось.";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Назва книги", entry.title());
        assertEquals("Автор", entry.author());
        assertEquals("Дуже гарна книга, рекомендую прочитати.\nМені сподобалось.", entry.reviewNote());
    }

    @Test
    void parseWithReviewNote_doubleQuotes() {
        String text = "John Smith \"Great Book\"\nAn excellent read about life.";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Great Book", entry.title());
        assertEquals("John Smith", entry.author());
        assertEquals("An excellent read about life.", entry.reviewNote());
    }

    @Test
    void noBookPattern_returnsNull() {
        String text = "Just a casual message about nothing";
        BookEntry entry = parser.parseBook(text);
        assertNull(entry);
    }

    @Test
    void emptyMessage_returnsNull() {
        BookEntry entry = parser.parseBook("");
        assertNull(entry);
    }

    @Test
    void nullMessage_returnsNull() {
        BookEntry entry = parser.parseBook(null);
        assertNull(entry);
    }

    @Test
    void onlyLink_returnsNull() {
        String text = "https://example.com/some-article";
        BookEntry entry = parser.parseBook(text);
        assertNull(entry);
    }

    @Test
    void multipleAuthorsInName() {
        String text = "Chip Heath, Dan Heath «Ідеї, що запам'ятовуються»";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Ідеї, що запам'ятовуються", entry.title());
        assertEquals("Chip Heath, Dan Heath", entry.author());
    }

    @Test
    void titleWithSpecialCharacters() {
        String text = "Автор «Книга: підзаголовок — нотатки»";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Книга: підзаголовок — нотатки", entry.title());
    }

    @Test
    void trimWhitespace() {
        String text = "  Author  «  Title  »  ";
        BookEntry entry = parser.parseBook(text);
        assertNotNull(entry);
        assertEquals("Title", entry.title());
        assertEquals("Author", entry.author());
    }
}
