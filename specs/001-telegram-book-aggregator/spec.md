# Feature Specification: Telegram Book & Quote Aggregator

**Feature Branch**: `001-telegram-book-aggregator`
**Created**: 2026-02-16
**Status**: Draft
**Input**: User description: "Telegram channel book/quote aggregator with OCR, searchable quotes, book stats, and website"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Channel Ingestion & Book Catalog (Priority: P1)

As the channel owner, I want the system to read my entire Telegram
channel history, identify book entries (title and author), and display
them as a browsable book list on a website so I have a permanent,
organized record of everything I have read.

**Why this priority**: This is the foundational data pipeline. Without
ingesting the channel and extracting book data, no other feature
(quotes, search, stats) can function. A browsable book list alone
delivers immediate value as a personal reading archive.

**Independent Test**: Can be fully tested by pointing the system at a
Telegram channel, running ingestion, and verifying that the website
displays a complete list of books with titles and authors matching what
was posted in the channel.

**Acceptance Scenarios**:

1. **Given** a Telegram channel with 100+ messages containing book posts,
   **When** the system runs a full history sync,
   **Then** every book post is parsed and stored with its title and author.
2. **Given** the ingestion has completed,
   **When** I open the website,
   **Then** I see a paginated list of all books sorted by most recently
   posted.
3. **Given** a new book is posted to the channel after initial sync,
   **When** the system runs an incremental sync,
   **Then** the new book appears on the website without re-processing
   the entire history.
4. **Given** the same book is posted in multiple messages,
   **When** ingestion completes,
   **Then** only one book entry exists (duplicates are merged).

---

### User Story 2 - Quote Extraction with OCR & Search (Priority: P2)

As the channel owner, I want all quotes from my channel (both plain
text quotes and text extracted from photos via OCR) to be stored,
associated with their books, and searchable through the website so I
can quickly find any passage I have saved.

**Why this priority**: Quotes are the richest content in the channel.
Making them searchable transforms a passive archive into an active
reference tool. OCR is required because many quotes are posted as
photos of book pages.

**Independent Test**: Can be tested by ingesting a channel that
contains both text quotes and photo quotes, then searching for a known
phrase on the website and verifying it appears in results with the
correct book attribution.

**Acceptance Scenarios**:

1. **Given** a channel message contains a text quote attributed to a
   book,
   **When** ingestion runs,
   **Then** the quote is stored and linked to the correct book.
2. **Given** a channel message contains a photo of a book page,
   **When** ingestion runs,
   **Then** OCR extracts the text and stores it as a quote linked to
   the book.
3. **Given** an OCR extraction produces low-confidence results,
   **When** ingestion completes,
   **Then** the quote is flagged for manual review and the original
   photo is preserved.
4. **Given** quotes have been ingested,
   **When** I type a search query on the website,
   **Then** I see all matching quotes with highlighted search terms and
   their associated book titles.
5. **Given** a search query with no matches,
   **When** I submit the search,
   **Then** I see a clear "no results" message.

---

### User Story 3 - Book Filtering by Genre & Author Country (Priority: P3)

As the channel owner, I want to sort and filter my book list by genre
and by the country of the author so I can explore my reading patterns
geographically and by literary category.

**Why this priority**: Filtering adds analytical depth to the book
catalog. It depends on metadata enrichment (genre, author country)
which may come from external sources, making it a natural extension
after the core catalog is built.

**Independent Test**: Can be tested by verifying that the book list
supports filtering by genre dropdown and country dropdown, and that
the results accurately reflect the metadata assigned to each book.

**Acceptance Scenarios**:

1. **Given** books have genre metadata assigned,
   **When** I select a genre from the filter,
   **Then** only books matching that genre are displayed.
2. **Given** books have author country metadata assigned,
   **When** I select a country from the filter,
   **Then** only books by authors from that country are displayed.
3. **Given** I apply both genre and country filters simultaneously,
   **When** viewing the list,
   **Then** only books matching both criteria are shown.
4. **Given** a book has no genre or country metadata yet,
   **When** I view the unfiltered list,
   **Then** the book still appears with "Unknown" displayed for the
   missing metadata.

---

### User Story 4 - Reading Statistics Dashboard (Priority: P4)

As the channel owner, I want to see statistics about my reading habits
including total book count, time it takes me to read each book, and
reading pace over time so I can track and reflect on my reading
activity.

**Why this priority**: Stats add a motivational and reflective layer
but are not essential for the core archival function. They depend on
having an established book catalog with timestamps.

**Independent Test**: Can be tested by ingesting a channel with known
book start/end dates and verifying that the stats page shows correct
counts and durations.

**Acceptance Scenarios**:

1. **Given** the book catalog is populated,
   **When** I navigate to the stats page,
   **Then** I see the total number of books read.
2. **Given** the first quote for a book was posted on Jan 1 and the
   book title/author announcement was posted on Jan 15,
   **When** I view that book's detail,
   **Then** the reading duration shows 14 days.
3. **Given** I have read books over multiple months,
   **When** I view the stats page,
   **Then** I see a timeline or chart showing books read per month.
4. **Given** a book was mentioned only once (single message),
   **When** I view its reading duration,
   **Then** it shows "Single session" or similar rather than "0 days".

---

### Edge Cases

- What happens when a Telegram message contains no recognizable book
  or quote content (e.g., a casual comment or link)? The system MUST
  skip it gracefully without creating empty records.
- What happens when a photo is too blurry or low-resolution for OCR?
  The system MUST store the photo reference and flag the entry for
  manual review rather than discarding it.
- What happens when an author's name is spelled differently across
  messages (e.g., "Dostoevsky" vs "Dostoyevsky")? The system MUST
  treat minor spelling variations as potential duplicates and flag
  them for review.
- What happens when the Telegram channel is unreachable during a sync
  attempt? The system MUST retry with backoff and report the failure
  without losing previously ingested data.
- What happens when quotes contain non-Latin scripts (Cyrillic,
  Arabic, CJK)? OCR and search MUST support multilingual text.
- What happens when a book has multiple authors? The system MUST
  support storing and displaying multiple authors per book.

## Clarifications

### Session 2026-02-16

- Q: How are quotes associated with their book? → A: Quotes are posted *before* the book announcement (while reading). The book title/author post comes after finishing. Associate each quote with the next book mentioned chronologically (i.e., the book post that follows the quote).
- Q: Should review/commentary text in book posts be stored? → A: Yes, store as a note on the book and display on the book detail page.
- Q: How is sync triggered? → A: Manual trigger only (user clicks a "sync now" button on the website).
- Q: How are flagged items reviewed? → A: Review queue on the website where user can view flagged items, edit/correct OCR text, merge duplicates, assign books, and approve or dismiss.
- Q: Is the website publicly accessible or protected? → A: Password-protected with a simple single-password gate.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST connect to a specified Telegram channel and
  retrieve the complete message history on first run.
- **FR-002**: System MUST perform incremental syncs to capture new
  messages posted after the initial import. Sync is triggered
  manually by the user via a "sync now" button on the website.
- **FR-003**: System MUST parse book entries from channel messages,
  extracting the title and author. The title is always enclosed in
  quotation marks (either «guillemets» or "double quotes"). The
  author name appears outside the quotation marks, either before or
  after the title. Both orders MUST be supported:
  - Author «Title» (e.g., Майґуль Аксельссон «Квітнева відьма»)
  - «Title» Author (e.g., «Червона Королева» Метт Рідлі)
  - "Title" Author (e.g., "The way of the superior man" David Deida)
  - Author "Title" (e.g., Walter J. Ong "Orality and Literacy")
  Messages may contain a review or commentary following the book
  line; this text MUST NOT be treated as part of the title or author.
  Review text MUST be stored as a note on the book and displayed on
  the book's detail page.
- **FR-004**: System MUST extract text from photo messages using OCR,
  supporting multilingual content.
- **FR-005**: System MUST associate extracted quotes (both text and
  OCR) with their corresponding books. Quotes are posted *before*
  the book title/author announcement (during reading). The system
  MUST associate each quote with the next book entry that appears
  chronologically after it in the channel.
- **FR-006**: System MUST detect and merge duplicate book entries
  caused by repeated mentions or minor spelling variations.
- **FR-007**: System MUST enrich book metadata (genre, author country)
  from external data sources.
- **FR-008**: System MUST provide a web interface displaying a
  paginated, sortable list of all books.
- **FR-009**: System MUST allow filtering books by genre and by
  author country on the web interface.
- **FR-010**: System MUST provide full-text search across all stored
  quotes, returning results with book attribution.
- **FR-011**: System MUST compute and display reading statistics:
  total book count, per-book reading duration (based on first and
  last mention timestamps), and books-per-month trend.
- **FR-012**: System MUST flag low-confidence OCR results and
  unrecognized messages for manual review.
- **FR-016**: System MUST require a single shared password to access
  the website. All pages (book list, quotes, stats, review queue,
  sync) MUST be inaccessible without authentication.
- **FR-015**: System MUST provide a review queue on the website where
  the user can view all flagged items, edit/correct OCR text, merge
  duplicate books, assign orphaned quotes to books, and approve or
  dismiss each flagged entry.
- **FR-013**: System MUST preserve original Telegram message IDs for
  traceability back to the source channel.
- **FR-014**: System MUST store original photo files alongside
  extracted OCR text.

### Key Entities

- **Book**: Represents a book read by the user. Key attributes: title,
  author(s), genre, author country, date first mentioned, date last
  mentioned, reading duration, cover photo (if posted), review note
  (commentary text from the book announcement message, if present).
- **Quote**: A passage from a book. Key attributes: text content,
  source type (text message or OCR from photo), associated book,
  original Telegram message ID, extraction confidence score (for OCR).
- **TelegramMessage**: A raw message from the channel. Key attributes:
  message ID, date posted, message type (text, photo, other),
  processing status (pending, processed, skipped, flagged).
- **Photo**: An image posted in the channel. Key attributes: Telegram
  file reference, local stored path, OCR-extracted text, OCR
  confidence score.
- **ReadingStats**: Computed statistics. Key attributes: total book
  count, per-book reading duration, monthly reading count, average
  reading pace.

## Assumptions

- The channel owner has admin or bot access to the Telegram channel,
  enabling full history retrieval via the Telegram API.
- The channel is primarily used for posting books and quotes (not a
  general-purpose chat), so most messages are relevant content.
- Book metadata enrichment (genre, author country) will use freely
  available public APIs (e.g., Open Library) and may not cover every
  book; missing metadata is acceptable and shown as "Unknown".
- The website is a personal tool for the channel owner (single user).
  Multi-user accounts are out of scope. Access is gated by a simple
  single-password authentication.
- Reading duration is computed from Telegram message timestamps:
  the first quote associated with a book marks the start of reading,
  and the book title/author announcement post marks the finish date.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of book posts in the channel are correctly parsed
  and appear in the website book list after full ingestion.
- **SC-002**: 90% of photo-based quotes have their text successfully
  extracted via OCR (remaining 10% are flagged for review).
- **SC-003**: Quote search returns relevant results in under 2 seconds
  for a catalog of up to 1,000 quotes.
- **SC-004**: Users can filter the book list by genre or country and
  see updated results in under 1 second.
- **SC-005**: Reading duration statistics are accurate to within 1 day
  compared to the actual first and last Telegram message dates for
  each book.
- **SC-006**: Manual sync completes and reflects new channel messages
  on the website within 60 seconds of the user triggering it (for
  up to 100 new messages).
- **SC-007**: The system handles a channel history of at least 5,000
  messages without failure or data loss.
