package com.chatagg.sync;

import com.chatagg.config.AppConfig;
import com.chatagg.db.*;
import com.chatagg.enrichment.OpenLibraryClient;
import com.chatagg.enrichment.WikidataClient;
import com.chatagg.model.*;
import com.chatagg.ocr.OcrService;
import com.chatagg.ocr.OcrService.OcrResult;
import com.chatagg.telegram.MessageParser;
import com.chatagg.telegram.MessageParser.BookEntry;
import com.chatagg.telegram.TelegramClient;
import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final AppConfig config;
    private final TelegramClient telegramClient;
    private final MessageParser messageParser;
    private final OcrService ocrService;
    private final BookDao bookDao;
    private final AuthorDao authorDao;
    private final TelegramMessageDao telegramMessageDao;
    private final SyncStateDao syncStateDao;
    private final QuoteDao quoteDao;
    private final PhotoDao photoDao;
    private final OpenLibraryClient openLibraryClient;
    private final WikidataClient wikidataClient;

    private volatile boolean syncing;
    private SyncEventBus eventBus;

    public void setEventBus(SyncEventBus eventBus) {
        this.eventBus = eventBus;
    }

    private void emit(SyncEvent event) {
        if (eventBus != null) {
            eventBus.emit(event);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    public record SyncResult(
            int newMessages,
            int booksFound,
            int quotesFound,
            int photosProcessed,
            int flaggedItems,
            double durationSeconds
    ) {}

    public SyncService(AppConfig config, DatabaseManager db,
                       TelegramClient telegramClient, MessageParser messageParser,
                       OcrService ocrService) {
        this.config = config;
        this.telegramClient = telegramClient;
        this.messageParser = messageParser;
        this.ocrService = ocrService;
        this.bookDao = new BookDao(db);
        this.authorDao = new AuthorDao(db);
        this.telegramMessageDao = new TelegramMessageDao(db);
        this.syncStateDao = new SyncStateDao(db);
        this.quoteDao = new QuoteDao(db);
        this.photoDao = new PhotoDao(db);
        this.openLibraryClient = new OpenLibraryClient();
        this.wikidataClient = new WikidataClient();
    }

    public SyncResult sync() {
        if (syncing) {
            throw new IllegalStateException("Sync is already in progress");
        }

        syncing = true;
        long startTime = System.nanoTime();

        int newMessages = 0;
        int booksFound = 0;
        int quotesFound = 0;
        int photosProcessed = 0;
        int flaggedItems = 0;

        try {
            // Ensure Telegram client is authenticated
            emit(new SyncEvent.Connecting());
            try {
                telegramClient.start();
            } catch (TelegramClient.AuthRequiredException e) {
                String authType = switch (e.getAuthState()) {
                    case WAITING_CODE -> "code";
                    case WAITING_PASSWORD -> "password";
                    default -> "unknown";
                };
                emit(new SyncEvent.AuthRequired(authType));
                throw new RuntimeException("Telegram authentication required", e);
            } catch (Exception e) {
                log.error("Failed to start Telegram client", e);
                emit(new SyncEvent.Error("Failed to connect to Telegram: " + e.getMessage()));
                throw new RuntimeException("Failed to connect to Telegram", e);
            }

            // Guard: don't proceed unless fully authenticated
            TelegramClient.AuthState currentAuth = telegramClient.getAuthState();
            if (currentAuth != TelegramClient.AuthState.READY) {
                String authType = switch (currentAuth) {
                    case WAITING_CODE -> "code";
                    case WAITING_PASSWORD -> "password";
                    default -> "unknown";
                };
                emit(new SyncEvent.AuthRequired(authType));
                throw new RuntimeException("Telegram authentication required",
                        new TelegramClient.AuthRequiredException(currentAuth));
            }

            long channelChatId = config.telegramChannelId;
            SyncState syncState = syncStateDao.getOrCreate(channelChatId);
            log.info("Starting sync for channel {}, lastMessageId={}", channelChatId, syncState.getLastMessageId());

            List<TdApi.Message> messages;
            try {
                if (syncState.getLastMessageId() == null) {
                    log.info("No previous sync state found, fetching full chat history");
                    messages = telegramClient.getFullChatHistory(channelChatId);
                } else {
                    log.info("Incremental sync, fetching messages newer than messageId={}", syncState.getLastMessageId());
                    messages = telegramClient.getMessagesSince(channelChatId, syncState.getLastMessageId());
                }
            } catch (Exception e) {
                log.error("Failed to fetch chat history from Telegram", e);
                throw new RuntimeException("Failed to fetch chat history", e);
            }

            // TDLib returns newest first, process in chronological order
            Collections.reverse(messages);
            emit(new SyncEvent.Fetched(messages.size()));
            log.info("Fetched {} messages from Telegram to process", messages.size());
            if (messages.isEmpty()) {
                log.warn("No messages returned from Telegram. This may indicate the chat isn't fully loaded in TDLib.");
            }

            long latestMessageId = syncState.getLastMessageId() != null ? syncState.getLastMessageId() : 0;
            int skippedDuplicates = 0;

            // Track newly created IDs for targeted enrichment (don't touch existing data)
            List<Long> newBookIds = new ArrayList<>();
            List<Long> newAuthorIds = new ArrayList<>();

            // Track pending quotes to associate with the next book (FR-005)
            List<PendingQuote> pendingQuotes = new ArrayList<>();

            for (TdApi.Message tdMsg : messages) {
                try {
                    if (tdMsg.id > latestMessageId) {
                        latestMessageId = tdMsg.id;
                    }

                    TelegramMessage tgMessage = new TelegramMessage();
                    tgMessage.setTelegramMessageId(tdMsg.id);
                    tgMessage.setChatId(channelChatId);
                    tgMessage.setMessageDate(Instant.ofEpochSecond(tdMsg.date));

                    String rawText = null;
                    String messageType;

                    if (tdMsg.content instanceof TdApi.MessageText textContent) {
                        messageType = "text";
                        rawText = textContent.text.text;
                    } else if (tdMsg.content instanceof TdApi.MessagePhoto photoContent) {
                        messageType = "photo";
                        rawText = photoContent.caption.text;
                    } else {
                        messageType = "other";
                    }

                    tgMessage.setMessageType(messageType);
                    tgMessage.setRawText(rawText);
                    tgMessage.setProcessingStatus("pending");

                    long storedId = telegramMessageDao.insert(tgMessage);
                    if (storedId == 0) {
                        skippedDuplicates++;
                        log.debug("Message {} already exists, skipping", tdMsg.id);
                        continue;
                    }

                    newMessages++;

                    if (tdMsg.content instanceof TdApi.MessageText) {
                        BookEntry bookEntry = messageParser.parseBook(rawText);
                        if (bookEntry != null) {
                            // Book announcement found
                            Book book = new Book();
                            book.setTitle(bookEntry.title());
                            book.setReviewNote(bookEntry.reviewNote());
                            book.setAnnouncementDate(Instant.ofEpochSecond(tdMsg.date));
                            book.setTelegramMessageId(tdMsg.id);

                            List<Book> duplicates = bookDao.findPotentialDuplicates(bookEntry.title(), bookEntry.author());
                            if (!duplicates.isEmpty()) {
                                log.warn("Potential duplicate for '{}' by '{}', flagging", bookEntry.title(), bookEntry.author());
                                telegramMessageDao.updateProcessingStatus(storedId, "flagged");
                                flaggedItems++;
                                emit(new SyncEvent.Flagged("possible duplicate: " + bookEntry.title()));
                            } else {
                                long bookId = bookDao.insert(book);
                                newBookIds.add(bookId);
                                long authorId = authorDao.insertOrFind(bookEntry.author());
                                newAuthorIds.add(authorId);
                                authorDao.linkToBook(bookId, authorId);

                                // Associate pending quotes with this book (FR-005)
                                for (PendingQuote pq : pendingQuotes) {
                                    quoteDao.updateBookId(pq.quoteId, bookId);
                                }
                                if (!pendingQuotes.isEmpty()) {
                                    log.info("Associated {} pending quotes with book '{}'", pendingQuotes.size(), bookEntry.title());
                                }
                                pendingQuotes.clear();

                                telegramMessageDao.updateProcessingStatus(storedId, "processed");
                                booksFound++;
                                emit(new SyncEvent.BookFound(bookEntry.title(), bookEntry.author()));
                                log.info("Book created: '{}' by '{}' (bookId={})", bookEntry.title(), bookEntry.author(), bookId);
                            }
                        } else {
                            // Text quote - create quote record
                            if (rawText != null && !rawText.isBlank()) {
                                Quote quote = new Quote();
                                quote.setTextContent(rawText);
                                quote.setSourceType("text");
                                quote.setTelegramMessageId(tdMsg.id);
                                quote.setTelegramMessageDate(Instant.ofEpochSecond(tdMsg.date));
                                quote.setReviewStatus("approved");

                                long quoteId = quoteDao.insert(quote);
                                pendingQuotes.add(new PendingQuote(quoteId, Instant.ofEpochSecond(tdMsg.date)));
                                quotesFound++;
                                emit(new SyncEvent.QuoteFound(truncate(rawText, 80)));
                            }
                            telegramMessageDao.updateProcessingStatus(storedId, "processed");
                        }
                    } else if (tdMsg.content instanceof TdApi.MessagePhoto photoContent) {
                        // Photo message - download, OCR, create photo + quote
                        TdApi.PhotoSize[] sizes = photoContent.photo.sizes;
                        if (sizes.length > 0) {
                            TdApi.PhotoSize largest = sizes[sizes.length - 1];
                            try {
                                byte[] imageBytes = telegramClient.downloadFile(largest.photo.id);

                                // Store photo locally
                                Path photoDir = Path.of(config.photoStoragePath);
                                Files.createDirectories(photoDir);
                                String filename = tdMsg.id + ".jpg";
                                Path photoPath = photoDir.resolve(filename);
                                Files.write(photoPath, imageBytes);

                                // OCR disabled — skip extraction
                                OcrResult ocrResult = new OcrResult("", 0f);

                                Photo photo = new Photo();
                                photo.setTelegramFileId(largest.photo.remote.id);
                                photo.setLocalPath(photoPath.toString());
                                photo.setOcrText(ocrResult.text());
                                photo.setOcrConfidence(ocrResult.confidence());
                                long photoId = photoDao.insert(photo);

                                // Always create a quote for photo messages (OCR text may be empty)
                                Quote quote = new Quote();
                                quote.setTextContent(ocrResult.text().isBlank() ? "" : ocrResult.text());
                                quote.setSourceType("photo");
                                quote.setTelegramMessageId(tdMsg.id);
                                quote.setTelegramMessageDate(Instant.ofEpochSecond(tdMsg.date));
                                quote.setOcrConfidence(ocrResult.confidence());
                                quote.setPhotoId(photoId);

                                // Photo quotes are always approved — the image is the primary content
                                boolean isFlagged = false;
                                quote.setReviewStatus(isFlagged ? "flagged" : "approved");

                                long quoteId = quoteDao.insert(quote);
                                if (!isFlagged) {
                                    pendingQuotes.add(new PendingQuote(quoteId, Instant.ofEpochSecond(tdMsg.date)));
                                } else {
                                    flaggedItems++;
                                }
                                quotesFound++;

                                photosProcessed++;
                                emit(new SyncEvent.PhotoProcessed());
                            } catch (Exception e) {
                                log.error("Failed to process photo for message {}", tdMsg.id, e);
                            }
                        }
                        telegramMessageDao.updateProcessingStatus(storedId, "processed");
                    } else {
                        telegramMessageDao.updateProcessingStatus(storedId, "skipped");
                    }
                } catch (Exception e) {
                    log.error("Error processing message {}", tdMsg.id, e);
                }
            }

            // Any remaining pending quotes are orphans - flag them
            for (PendingQuote pq : pendingQuotes) {
                quoteDao.updateReviewStatus(pq.quoteId, "flagged");
                flaggedItems++;
            }
            if (!pendingQuotes.isEmpty()) {
                log.info("Flagged {} orphan quotes with no subsequent book", pendingQuotes.size());
            }

            // Update sync state
            if (latestMessageId > 0) {
                syncStateDao.updateLastMessageId(syncState.getId(), latestMessageId);
            }
            syncStateDao.updateLastSyncAt(syncState.getId());
            syncStateDao.incrementProcessedCount(syncState.getId(), newMessages);

            // Enrichment step (US3): only enrich newly synced books/authors (don't touch existing data)
            if (booksFound > 0) {
                enrichBooks(newBookIds, newAuthorIds);
            }

            double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            log.info("Sync complete: {} fetched from Telegram, {} new, {} duplicates skipped, {} books, {} quotes, {} photos, {} flagged in {}s",
                    messages.size(), newMessages, skippedDuplicates, booksFound, quotesFound, photosProcessed, flaggedItems,
                    String.format("%.2f", durationSeconds));

            emit(new SyncEvent.Done(newMessages, booksFound, quotesFound, photosProcessed, flaggedItems, durationSeconds));
            return new SyncResult(newMessages, booksFound, quotesFound, photosProcessed, flaggedItems, durationSeconds);
        } catch (Exception e) {
            // AuthRequired is already emitted at source; only emit Error for other failures
            boolean isAuth = false;
            Throwable c = e;
            while (c != null) {
                if (c instanceof TelegramClient.AuthRequiredException) {
                    isAuth = true;
                    break;
                }
                c = c.getCause();
            }
            if (!isAuth) {
                emit(new SyncEvent.Error(e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        } finally {
            syncing = false;
        }
    }

    /** Manual enrichment: enriches all books/authors with NULL genre/country. */
    public void enrichBooks() {
        enrichBooks(null, null);
    }

    /** Targeted enrichment: if IDs are provided, only enrich those; otherwise enrich all. */
    public void enrichBooks(List<Long> bookIds, List<Long> authorIds) {
        // Enrich author countries via Wikidata
        List<Author> authors = (authorIds != null) ? authorDao.findWithoutCountryByIds(authorIds) : authorDao.findWithoutCountry();
        emit(new SyncEvent.EnrichingAuthors(authors.size()));
        log.info("Enriching {} new authors without country data", authors.size());
        int enrichedAuthors = 0;

        for (Author author : authors) {
            try {
                String country = wikidataClient.searchByName(author.getName());
                if (country != null) {
                    authorDao.updateCountry(author.getId(), country);
                    enrichedAuthors++;
                    log.info("Enriched author '{}' with country '{}'", author.getName(), country);
                }
            } catch (Exception e) {
                log.warn("Enrichment failed for author '{}': {}", author.getName(), e.getMessage());
            }
        }

        log.info("Author enrichment complete: {}/{} new authors enriched", enrichedAuthors, authors.size());

        // Enrich book genres via OpenLibrary subjects
        List<Map<String, Object>> booksWithoutGenre = (bookIds != null) ? bookDao.findWithoutGenreByIds(bookIds) : bookDao.findWithoutGenre();
        emit(new SyncEvent.EnrichingBooks(booksWithoutGenre.size()));
        log.info("Enriching {} new books without genre data", booksWithoutGenre.size());
        int enrichedGenres = 0;

        for (Map<String, Object> bookInfo : booksWithoutGenre) {
            long bookId = (long) bookInfo.get("id");
            String title = (String) bookInfo.get("title");
            String authorName = (String) bookInfo.get("author_name");

            try {
                OpenLibraryClient.SearchResult result = openLibraryClient.searchByTitleAndAuthor(
                        title, authorName != null ? authorName : "");
                if (result.subjects().isEmpty()) {
                    log.debug("No subjects found for book '{}' — skipping genre classification", title);
                    continue;
                }
                String genre = OpenLibraryClient.classifyGenre(result.subjects());
                if (genre != null) {
                    bookDao.updateGenre(bookId, genre, "openlibrary");
                    enrichedGenres++;
                    log.info("Enriched book '{}' with genre '{}' (subjects: {})", title, genre, result.subjects());
                }
            } catch (Exception e) {
                log.warn("Genre enrichment failed for book '{}': {}", title, e.getMessage());
            }
        }

        log.info("Genre enrichment complete: {}/{} new books enriched", enrichedGenres, booksWithoutGenre.size());
    }

    public boolean isSyncing() {
        return syncing;
    }

    private record PendingQuote(long quoteId, Instant messageDate) {}
}
