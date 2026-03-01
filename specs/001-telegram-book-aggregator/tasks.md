# Tasks: Telegram Book & Quote Aggregator

**Input**: Design documents from `/specs/001-telegram-book-aggregator/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.yaml, quickstart.md

**Tests**: Included per constitution principle V (Test-First Development). Unit tests for non-trivial parsing/OCR logic; integration tests for DAO round-trips and sync orchestration; contract tests for API validation.

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths included in descriptions

## Path Conventions

- **Backend**: `backend/src/main/java/com/chatagg/`
- **Backend tests**: `backend/src/test/java/com/chatagg/`
- **Backend resources**: `backend/src/main/resources/`
- **Frontend**: `frontend/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven project initialization, Docker configuration, environment template

- [x] T001 Create Maven project structure with backend/pom.xml containing all dependencies: Javalin 6.7.0, TDLight-Java 3.4.4+td.1.8.52, Tess4J 5.16.0, HikariCP 5.1.0, Flyway 10.8.0, PostgreSQL JDBC 42.7.3, JUnit 5, Testcontainers, Mockito; configure maven-shade-plugin for fat JAR with mainClass com.chatagg.App
- [x] T002 [P] Create .env.example at repository root with all configuration variables per quickstart.md: TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_PHONE, TELEGRAM_CHANNEL_ID, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, APP_PASSWORD, APP_PORT, TESSERACT_DATA_PATH, OCR_CONFIDENCE_THRESHOLD, PHOTO_STORAGE_PATH
- [x] T003 [P] Create Dockerfile at repository root using eclipse-temurin:21-jre-alpine base, install Tesseract OCR + ukr/eng language packs, copy fat JAR, expose APP_PORT, set ENTRYPOINT to java -jar
- [x] T004 [P] Create docker-compose.yml at repository root with two services: app (build from Dockerfile, depends_on postgres, env_file .env, ports APP_PORT, volumes for photos and tdlib-session) and postgres (postgres:16-alpine, env for POSTGRES_DB/USER/PASSWORD, volume for data persistence)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 Create AppConfig.java in backend/src/main/java/com/chatagg/config/ that reads all environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, APP_PASSWORD, APP_PORT, TELEGRAM_API_ID, TELEGRAM_API_HASH, TELEGRAM_PHONE, TELEGRAM_CHANNEL_ID, TESSERACT_DATA_PATH, OCR_CONFIDENCE_THRESHOLD, PHOTO_STORAGE_PATH) with sensible defaults for local development
- [x] T006 Create all model POJOs in backend/src/main/java/com/chatagg/model/: Book.java (id, title, reviewNote, genre, announcementDate, firstQuoteDate, readingDurationDays, telegramMessageId, coverPhotoPath, metadataSource, createdAt, updatedAt), Author.java (id, name, country, wikidataId, openlibraryId, createdAt), Quote.java (id, bookId, textContent, sourceType, telegramMessageId, telegramMessageDate, ocrConfidence, photoId, reviewStatus, createdAt), Photo.java (id, telegramFileId, localPath, ocrText, ocrConfidence, createdAt), TelegramMessage.java (id, telegramMessageId, chatId, messageDate, messageType, rawText, processingStatus, processedAt), SyncState.java (id, channelChatId, lastMessageId, lastSyncAt, totalMessagesProcessed), ReviewItem.java (id, type, telegramMessageId, messageDate, rawText, ocrText, ocrConfidence, photoPath, suggestedBookId, suggestedBookTitle)
- [x] T007 Create Flyway migration V1__create_tables.sql in backend/src/main/resources/db/migration/ implementing all 7 tables from data-model.md: book, author, book_author, quote, photo, telegram_message, sync_state with all columns, constraints, foreign keys, and indexes (telegram_message_id UNIQUE, processing_status, message_date, book_id on quote, review_status on quote)
- [x] T008 Create Flyway migration V2__add_search_index.sql in backend/src/main/resources/db/migration/ adding: tsvector column search_vector on quote table, GIN index on search_vector, trigger function update_quote_search_vector() using to_tsvector('simple', NEW.text_content), trigger on quote BEFORE INSERT OR UPDATE
- [x] T009 Create DatabaseManager.java in backend/src/main/java/com/chatagg/db/ that initializes HikariCP connection pool from AppConfig, runs Flyway migrations on startup, and provides getConnection() method
- [x] T010 [P] Implement AuthMiddleware.java in backend/src/main/java/com/chatagg/api/ that checks HTTP Basic Auth on all /api/* routes, comparing password against AppConfig.APP_PASSWORD, returning 401 Unauthorized on failure per api.yaml security scheme
- [x] T011 Create App.java in backend/src/main/java/com/chatagg/ as the main entry point: initialize AppConfig, DatabaseManager, register AuthMiddleware, configure Javalin to serve static files from frontend/ directory, register all API route handlers (placeholder stubs initially), start on APP_PORT
- [x] T012 [P] Create frontend scaffolding: frontend/css/style.css (base styles for book list, detail, search, stats, review pages) and frontend/js/api.js (shared fetch wrapper with Basic Auth header injection, error handling, base URL config)

**Checkpoint**: Foundation ready - project compiles, Docker builds, database migrations run, auth works, Javalin serves static files. User story implementation can now begin.

---

## Phase 3: User Story 1 - Channel Ingestion & Book Catalog (Priority: P1) MVP

**Goal**: Ingest full Telegram channel history, parse book entries (title + author), store in database, display paginated book list on website with manual sync trigger.

**Independent Test**: Point system at a Telegram channel, run sync via website button, verify book list displays all books with correct titles and authors. Verify incremental sync captures new books. Verify duplicates are merged.

### Tests for User Story 1

> **Write these tests FIRST, ensure they FAIL before implementation (Constitution V)**

- [x] T013 [P] [US1] Write MessageParserTest.java in backend/src/test/java/com/chatagg/unit/ with test cases for: Author «Title» format (Cyrillic: Майґуль Аксельссон «Квітнева відьма»), «Title» Author format, Author "Title" format, "Title" Author format, message with review note after book line (review text must not be part of title/author), message with multiple authors, message with no book pattern (should return null), message with only a casual comment
- [x] T014 [P] [US1] Write BookDaoTest.java in backend/src/test/java/com/chatagg/integration/ using Testcontainers PostgreSQL: test insert and findById round-trip, test findAll with pagination and sorting (date_desc, title_asc), test findByTelegramMessageId uniqueness constraint, test duplicate detection by normalized title+author

### Implementation for User Story 1

- [x] T015 [US1] Implement MessageParser.java in backend/src/main/java/com/chatagg/telegram/ with regex patterns for book extraction: «guillemets» and "double quotes" title delimiters, author name before or after title, review note separation (text after the title+author line), return parsed BookEntry record (title, authorName, reviewNote) or null if no book pattern found
- [x] T016 [US1] Implement TelegramClient.java in backend/src/main/java/com/chatagg/telegram/ wrapping TDLight-Java: initialize TDLib client with API credentials from AppConfig, handle phone authentication flow (one-time), implement getChatHistory(chatId, fromMessageId, limit) returning list of TdApi.Message, implement downloadFile(fileId) returning byte[] for photo downloads, handle TDLib lifecycle (start/stop)
- [x] T017 [P] [US1] Implement TelegramMessageDao.java in backend/src/main/java/com/chatagg/db/ with methods: insert(TelegramMessage), findByTelegramMessageId(long), findByStatus(String status), updateProcessingStatus(long id, String status), existsByTelegramMessageId(long) for dedup check
- [x] T018 [P] [US1] Implement SyncStateDao.java in backend/src/main/java/com/chatagg/db/ with methods: getOrCreate(long channelChatId), updateLastMessageId(long id, long lastMessageId), updateLastSyncAt(long id), incrementProcessedCount(long id, long delta)
- [x] T019 [P] [US1] Implement BookDao.java in backend/src/main/java/com/chatagg/db/ with methods: insert(Book) returning generated id, findById(long), findAll(int page, int size, String sort, String genre, String country) returning paginated results with author names and quote count, findByTelegramMessageId(long), findPotentialDuplicates(String title, String authorName) using normalized comparison for duplicate detection per FR-006
- [x] T020 [P] [US1] Implement AuthorDao.java in backend/src/main/java/com/chatagg/db/ with methods: insertOrFind(String name) returning author id (find by normalized name to handle minor variations), findByBookId(long bookId), linkToBook(long bookId, long authorId) inserting into book_author, findAll()
- [x] T021 [US1] Implement SyncService.java in backend/src/main/java/com/chatagg/sync/ orchestrating: fullSync (paginate through entire channel history via TelegramClient, store each message in telegram_message via DAO, parse book entries via MessageParser, create book+author records, detect duplicates and flag for review, update sync_state); incrementalSync (fetch messages after last_message_id only); return SyncResult with counts per api.yaml schema
- [x] T022 [US1] Implement SyncController.java in backend/src/main/java/com/chatagg/api/ handling POST /api/sync per api.yaml: trigger sync via SyncService, return SyncResult JSON, return 409 if sync already in progress (use simple boolean lock)
- [x] T023 [US1] Implement BookController.java in backend/src/main/java/com/chatagg/api/ handling GET /api/books (paginated, sorted, with page/size/sort query params per api.yaml, genre/country filter params wired but functional in US3) and GET /api/books/{id} (full BookDetail with authors, reviewNote, quotes list, metadata per api.yaml BookDetail schema)
- [x] T024 [P] [US1] Create frontend/index.html and frontend/js/books.js: paginated book list table with columns (title, authors, genre, announcement date, reading duration, quote count), sort controls (date, title, duration), pagination controls, "Sync Now" button that calls POST /api/sync and shows progress/result, link each book title to book.html?id={id}
- [x] T025 [P] [US1] Create frontend/book.html and frontend/js/book-detail.js: display book title, authors, genre, announcement date, first quote date, reading duration (show "Single session" if null), review note section, cover photo if present, metadata source badge, list of associated quotes (placeholder until US2 populates quotes), link back to book list

**Checkpoint**: Full sync ingests channel, books display on website, incremental sync works, duplicates flagged. US1 is independently testable.

---

## Phase 4: User Story 2 - Quote Extraction with OCR & Search (Priority: P2)

**Goal**: Extract text quotes and photo quotes (via OCR) from channel messages, associate with books, enable full-text search, and provide a review queue for flagged items.

**Independent Test**: Ingest channel with text quotes and photo quotes, verify quotes appear under correct books, search for a known phrase and verify results with book attribution, check review queue shows low-confidence OCR items.

### Tests for User Story 2

> **Write these tests FIRST, ensure they FAIL before implementation (Constitution V)**

- [x] T026 [P] [US2] Write OcrServiceTest.java in backend/src/test/java/com/chatagg/unit/ with test cases for: extracting English text from a clear photo fixture, extracting Ukrainian/Cyrillic text, returning confidence score, handling low-confidence result (below threshold), handling empty/unreadable image gracefully
- [x] T027 [P] [US2] Write QuoteDaoTest.java and SearchTest.java in backend/src/test/java/com/chatagg/integration/ using Testcontainers PostgreSQL: test quote insert and findByBookId round-trip, test full-text search with tsvector (insert quotes, search for keyword, verify ranked results), test search with Cyrillic text, test search with no matches returns empty, test findFlagged filtering

### Implementation for User Story 2

- [x] T028 [US2] Implement OcrService.java in backend/src/main/java/com/chatagg/ocr/ wrapping Tess4J: initialize Tesseract with TESSERACT_DATA_PATH and language packs (ukr+eng), extractText(byte[] imageBytes) returning OcrResult record (text, confidence), use tessdata_best models, flag if confidence < OCR_CONFIDENCE_THRESHOLD from AppConfig
- [x] T029 [P] [US2] Implement PhotoDao.java in backend/src/main/java/com/chatagg/db/ with methods: insert(Photo) returning id, findById(long), findByTelegramFileId(String)
- [x] T030 [P] [US2] Implement QuoteDao.java in backend/src/main/java/com/chatagg/db/ with methods: insert(Quote) returning id, findByBookId(long bookId), search(String query, int page, int size) using plainto_tsquery('simple', query) with ts_rank ordering per data-model.md search strategy, findFlagged(int page, int size), updateReviewStatus(long id, String status), updateTextContent(long id, String correctedText), updateBookId(long id, long bookId)
- [x] T031 [US2] Extend SyncService.java to process quotes and photos: for each text message that is not a book announcement, create quote record; for each photo message, download via TelegramClient, store locally at PHOTO_STORAGE_PATH, run OcrService, create photo record, create quote record with source_type='photo'; associate quotes with next chronological book per FR-005; flag low-confidence OCR quotes (review_status='flagged'); flag orphan quotes with no subsequent book
- [x] T032 [US2] Implement QuoteController.java in backend/src/main/java/com/chatagg/api/ handling GET /api/quotes/search (full-text search with q, page, size params, return QuoteSearchResult with book_title, authors, rank per api.yaml) and GET /api/books/{id}/quotes (list all quotes for a book per api.yaml)
- [x] T033 [P] [US2] Implement ReviewDao.java in backend/src/main/java/com/chatagg/db/ with methods: findFlagged(int page, int size, String typeFilter) aggregating flagged quotes and flagged telegram_messages into ReviewItem list, approve(long id, String correctedText, Long bookId), dismiss(long id), mergeBooks(long keepId, List<Long> mergeIds) reassigning quotes and deleting merged books
- [x] T034 [US2] Implement ReviewController.java in backend/src/main/java/com/chatagg/api/ handling GET /api/review (paginated, filtered by type per api.yaml), PUT /api/review/{id}/approve (with optional correctedText and bookId), PUT /api/review/{id}/dismiss, POST /api/review/merge (keep_id + merge_ids per api.yaml)
- [x] T035 [P] [US2] Create frontend/search.html and frontend/js/search.js: search input field, submit on enter/button click, call GET /api/quotes/search, display results as cards with text_content (highlighted matches), book title link, author names, message date, show "No results found" for empty results
- [x] T036 [P] [US2] Create frontend/review.html and frontend/js/review.js: paginated list of flagged items with type filter tabs (all, quote, message, duplicate), each item shows raw_text/ocr_text, photo thumbnail if present, confidence score, suggested book; action buttons for approve (with text edit modal), dismiss, merge duplicates (select books to merge)
- [x] T037 [US2] Update frontend/js/book-detail.js to fetch and display quotes list from GET /api/books/{id}/quotes: show each quote's text, source type badge (text/photo), date, OCR confidence if photo-based

**Checkpoint**: Quotes extracted (text + OCR), associated with books, searchable via website, review queue functional. US2 is independently testable.

---

## Phase 5: User Story 3 - Book Filtering by Genre & Author Country (Priority: P3)

**Goal**: Enrich book metadata (genre, author country) from Open Library and Wikidata APIs, enable filtering the book list by genre and country dropdowns.

**Independent Test**: After sync, verify books have genre and country metadata populated. Select a genre from dropdown, verify only matching books shown. Select a country, verify filter works. Apply both filters simultaneously. Verify books with no metadata show "Unknown".

### Implementation for User Story 3

- [x] T038 [P] [US3] Implement OpenLibraryClient.java in backend/src/main/java/com/chatagg/enrichment/ using java.net.http.HttpClient: searchByTitleAndAuthor(String title, String author) returning subjects list and author Open Library key, respect 3 req/sec rate limit with User-Agent header, handle not-found gracefully returning empty
- [x] T039 [P] [US3] Implement WikidataClient.java in backend/src/main/java/com/chatagg/enrichment/ using java.net.http.HttpClient: lookupCountry(String wikidataId) extracting P27 (country of citizenship) property, searchByName(String authorName) as fallback if no Wikidata ID, return country name string or null
- [x] T040 [US3] Add enrichment step to SyncService.java: after book+author ingestion, for each book without metadata_source, call OpenLibraryClient to get genre/subjects, extract Wikidata ID from Open Library author, call WikidataClient for author country, update book.genre and author.country in database, set book.metadata_source to 'openlibrary'/'wikidata'
- [x] T041 [US3] Extend BookController.java to add GET /api/genres (SELECT DISTINCT genre FROM book WHERE genre IS NOT NULL) and GET /api/countries (SELECT DISTINCT country FROM author WHERE country IS NOT NULL) endpoints per api.yaml; verify existing GET /api/books genre/country filter params are functional in BookDao.findAll query
- [x] T042 [US3] Update frontend/js/books.js to add genre and country filter dropdowns: populate options from GET /api/genres and GET /api/countries on page load, on selection change re-fetch GET /api/books with filter params, show "Unknown" for books with null genre/country in the list

**Checkpoint**: Books enriched with genre + country from external APIs, filter dropdowns work, missing metadata shows "Unknown". US3 is independently testable.

---

## Phase 6: User Story 4 - Reading Statistics Dashboard (Priority: P4)

**Goal**: Compute and display reading statistics: total book count, per-book reading duration, books-per-month trend, genre/country distributions.

**Independent Test**: After sync with known book dates, verify stats page shows correct total count, reading durations match first-quote-to-announcement gaps, books-per-month chart reflects actual data, single-session books show appropriate label.

### Implementation for User Story 4

- [x] T043 [US4] Add reading duration computation to SyncService.java: after associating quotes with books, set book.first_quote_date to earliest quote's telegram_message_date, compute reading_duration_days as days between first_quote_date and announcement_date, set null (displayed as "Single session") if no quotes exist for a book; update BookDao with methods updateFirstQuoteDate(long bookId, Instant date) and updateReadingDuration(long bookId)
- [x] T044 [US4] Implement StatsController.java in backend/src/main/java/com/chatagg/api/ handling GET /api/stats per api.yaml: query total_books (COUNT from book), total_quotes (COUNT from quote WHERE review_status='approved'), total_photos (COUNT from photo), average_reading_days (AVG of reading_duration_days WHERE NOT NULL), books_per_month (GROUP BY month of announcement_date), genre_distribution (GROUP BY genre), country_distribution (GROUP BY author.country via book_author join)
- [x] T045 [US4] Create frontend/stats.html and frontend/js/stats.js: display summary cards (total books, total quotes, total photos, average reading days), books-per-month bar chart or timeline (use simple HTML/CSS bars or inline SVG, no chart library per YAGNI), genre distribution list, country distribution list, link to individual books from stats where applicable

**Checkpoint**: Stats page shows accurate reading statistics, durations correct, monthly trend visible. US4 is independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Edge case hardening, contract validation, deployment verification

- [x] T046 [P] Write ApiContractTest.java in backend/src/test/java/com/chatagg/contract/ using Testcontainers + Javalin test server: validate all 12 API endpoints return correct status codes and response shapes per contracts/api.yaml, test 401 without auth, test 404 for missing resources, test pagination params
- [x] T047 [P] Add retry logic with exponential backoff to TelegramClient.java for network failures during sync: retry up to 3 times with 1s/2s/4s delays, log failures, ensure partial sync progress is preserved in sync_state (no data loss on failure per edge case spec)
- [x] T048 Validate docker-compose end-to-end deployment: build image, start stack, verify Flyway migrations run, verify auth gate works, perform a manual sync, browse book list, search quotes, check stats, test review queue
- [x] T049 Run quickstart.md validation: follow every step in specs/001-telegram-book-aggregator/quickstart.md on a clean environment, verify all commands work, verify troubleshooting tips are accurate

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on T001 (pom.xml) completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - US1 (Phase 3): Can start after Phase 2
  - US2 (Phase 4): Depends on US1 (needs books to associate quotes with)
  - US3 (Phase 5): Depends on US1 (needs books to enrich)
  - US4 (Phase 6): Depends on US1 (needs books with dates) and US2 (needs quotes for duration computation)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

```
Phase 1: Setup
    │
    ▼
Phase 2: Foundational
    │
    ▼
Phase 3: US1 (Channel Ingestion & Book Catalog) ← MVP
    │
    ├──────────────┐
    ▼              ▼
Phase 4: US2    Phase 5: US3
(Quotes/OCR)    (Filtering)
    │              │
    ▼              │
Phase 6: US4 ◄────┘
(Statistics)
    │
    ▼
Phase 7: Polish
```

### Within Each User Story

1. Tests MUST be written and FAIL before implementation (Constitution V)
2. Models/DAOs before services
3. Services before controllers
4. Backend before frontend
5. Core implementation before integration points

### Parallel Opportunities

**Phase 1**: T002, T003, T004 all parallel (different files)
**Phase 2**: T006 + T010 + T012 parallel; T007 → T008 sequential (migration ordering)
**Phase 3 (US1)**: T013 + T014 parallel (tests); T017 + T018 + T019 + T020 parallel (DAOs); T024 + T025 parallel (frontend pages)
**Phase 4 (US2)**: T026 + T027 parallel (tests); T029 + T030 parallel (DAOs); T035 + T036 parallel (frontend pages)
**Phase 5 (US3)**: T038 + T039 parallel (API clients)
**Phase 7**: T046 + T047 parallel (different files)

---

## Parallel Example: User Story 1

```text
# Step 1: Launch tests in parallel (TDD - write failing tests first)
Task: T013 "Write MessageParserTest.java in backend/src/test/java/com/chatagg/unit/"
Task: T014 "Write BookDaoTest.java in backend/src/test/java/com/chatagg/integration/"

# Step 2: Implement parser (make T013 tests pass)
Task: T015 "Implement MessageParser.java"

# Step 3: Launch all DAOs in parallel
Task: T017 "Implement TelegramMessageDao.java"
Task: T018 "Implement SyncStateDao.java"
Task: T019 "Implement BookDao.java" (makes T014 tests pass)
Task: T020 "Implement AuthorDao.java"

# Step 4: Service + controllers (sequential, depends on DAOs)
Task: T016 "Implement TelegramClient.java"
Task: T021 "Implement SyncService.java"
Task: T022 "Implement SyncController.java"
Task: T023 "Implement BookController.java"

# Step 5: Launch frontend pages in parallel
Task: T024 "Create index.html + books.js"
Task: T025 "Create book.html + book-detail.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T004)
2. Complete Phase 2: Foundational (T005-T012)
3. Complete Phase 3: User Story 1 (T013-T025)
4. **STOP and VALIDATE**: Sync a Telegram channel, verify book list on website
5. Deploy if ready - book catalog alone delivers immediate value

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → Test: book list works → **Deploy (MVP!)**
3. US2 → Test: quotes + search + review work → Deploy
4. US3 → Test: genre/country filters work → Deploy
5. US4 → Test: stats dashboard works → Deploy
6. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable after its dependencies
- Constitution V mandates: write tests first, verify they fail, then implement
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
