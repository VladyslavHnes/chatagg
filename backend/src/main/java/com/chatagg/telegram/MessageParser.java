package com.chatagg.telegram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageParser {

    // Pattern 1: Author «Title» or «Title» Author (guillemets)
    private static final Pattern GUILLEMETS = Pattern.compile(
            "^\\s*(?:(.+?)\\s+)?[«\u00AB](.+?)[»\u00BB](?:\\s+(.+?))?\\s*$",
            Pattern.MULTILINE
    );

    // Pattern 2: Author "Title" or "Title" Author (double quotes)
    private static final Pattern DOUBLE_QUOTES = Pattern.compile(
            "^\\s*(?:(.+?)\\s+)?\u201C(.+?)\u201D(?:\\s+(.+?))?\\s*$",
            Pattern.MULTILINE
    );

    // Pattern 3: Author "Title" or "Title" Author (ASCII double quotes)
    private static final Pattern ASCII_QUOTES = Pattern.compile(
            "^\\s*(?:(.+?)\\s+)?\"(.+?)\"(?:\\s+(.+?))?\\s*$",
            Pattern.MULTILINE
    );

    public record BookEntry(String title, String author, String reviewNote) {}

    public BookEntry parseBook(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // Split into first line (book line) and remainder (review note)
        String[] lines = text.split("\\n", 2);
        String bookLine = lines[0].trim();
        String reviewNote = lines.length > 1 ? lines[1].trim() : null;
        if (reviewNote != null && reviewNote.isEmpty()) {
            reviewNote = null;
        }

        BookEntry entry = tryParse(bookLine, GUILLEMETS);
        if (entry == null) {
            entry = tryParse(bookLine, DOUBLE_QUOTES);
        }
        if (entry == null) {
            entry = tryParse(bookLine, ASCII_QUOTES);
        }

        if (entry == null) {
            return null;
        }

        return new BookEntry(entry.title(), entry.author(), reviewNote);
    }

    private BookEntry tryParse(String line, Pattern pattern) {
        Matcher m = pattern.matcher(line);
        if (!m.find()) {
            return null;
        }

        String beforeTitle = m.group(1);
        String title = m.group(2);
        String afterTitle = m.group(3);

        if (title == null || title.isBlank()) {
            return null;
        }
        title = title.trim();

        // Determine author: could be before or after the title
        String author;
        if (beforeTitle != null && !beforeTitle.isBlank()) {
            author = beforeTitle.trim();
        } else if (afterTitle != null && !afterTitle.isBlank()) {
            author = afterTitle.trim();
        } else {
            return null; // No author found
        }

        return new BookEntry(title, author, null);
    }
}
