# Research: Telegram Book & Quote Aggregator

**Branch**: `001-telegram-book-aggregator`
**Date**: 2026-02-16

## Decision 1: Telegram Channel Access

**Decision**: TDLight-Java (TDLib wrapper) with user account authentication

**Rationale**: The Telegram Bot API **cannot read channel history** -
bots only receive new messages from the point they are added. Full
history access requires TDLib with a user account. TDLight-Java is
the most actively maintained Java wrapper for TDLib.

**Alternatives considered**:
- Telegram Bot API (TelegramBots library): Cannot read history. Rejected.
- Kotlogram (raw MTProto): RC-quality, less maintained. Rejected.
- Official TDLib Java bindings: Less ergonomic than TDLight. Rejected.

**Key details**:
- Dependency: `it.tdlight:tdlight-java:3.4.4+td.1.8.52`
- Requires Telegram API credentials from https://my.telegram.org/apps
- User authenticates with phone number + code (one-time setup)
- `getChatHistory(chatId, fromMessageId, 0, 100, false)` for paginated
  history retrieval
- Store last `message_id` for incremental sync
- TDLib handles rate limiting internally

## Decision 2: OCR Engine

**Decision**: Tess4J (Tesseract 5.x wrapper for Java)

**Rationale**: Self-hosted, free, supports multilingual text (Ukrainian
+ English) via language packs. Provides confidence scores. Aligns with
constitution's simplicity principle - no cloud dependency needed.

**Alternatives considered**:
- Google Cloud Vision: Better accuracy but cloud-dependent, costs scale,
  privacy concern (photos sent to Google). Rejected for personal tool.
- Apache Tika + Tesseract: Additional overhead without benefit. Rejected.
- PaddleOCR: Python-native, complex Java integration. Rejected.

**Key details**:
- Dependency: `net.sourceforge.tess4j:tess4j:5.16.0`
- Language packs needed: `ukr.traineddata`, `eng.traineddata`
- Use `tessdata_best` model variant for book page accuracy
- Confidence via `getConfidence()` method (percentage)
- Flag for review if confidence < 70% (tunable threshold)
- System dependency: Tesseract OCR must be installed on host/container

## Decision 3: Backend Framework

**Decision**: Javalin 6.x (lightweight web framework)

**Rationale**: Simplest Java web framework that meets requirements.
Explicit routing (no annotation magic), minimal boilerplate, ~50-100MB
memory footprint. Aligns with YAGNI principle - Spring Boot's 300-500MB
footprint and ecosystem complexity is unjustified for a single-user
personal tool.

**Alternatives considered**:
- Spring Boot 3.x: Overkill. Spring Security, Spring Data JPA add
  layers of abstraction not needed for ~5 entities. Rejected.
- Quarkus: Better than Spring but still more complex than needed.
  Rejected.

**Key details**:
- Dependency: `io.javalin:javalin-bundle:6.7.0`
- Serves static frontend files directly
- HTTP Basic Auth for single-password protection
- HikariCP for connection pooling: `com.zaxxer:HikariCP:5.1.0`
- Direct JDBC for database access (no ORM needed for ~5 tables)

## Decision 4: Database & Full-Text Search

**Decision**: PostgreSQL 16 with native full-text search (tsvector)

**Rationale**: PostgreSQL handles both structured storage and full-text
search in a single service. For ~1,000 quotes, native FTS is more than
sufficient. Eliminates the operational burden of running Elasticsearch.

**Alternatives considered**:
- PostgreSQL + Elasticsearch: Overkill for scale. Rejected.
- Hibernate Search + Lucene (embedded): Adds ORM dependency. Rejected.

**Key details**:
- Dependency: `org.postgresql:postgresql:42.7.3`
- Use `'simple'` text search config for Ukrainian (no built-in Ukrainian
  stemmer; simple tokenization works well for exact-match search)
- Use `'english'` config for English content
- GIN index on tsvector columns for fast search
- Flyway for schema migrations: `org.flywaydb:flyway-core:10.8.0`

## Decision 5: Frontend

**Decision**: Plain HTML/CSS/JS with fetch() API

**Rationale**: Zero build step, no npm/node dependency. Served as static
files by Javalin. For a single-user tool with ~5 pages (book list, book
detail, quote search, stats, review queue), vanilla JS is sufficient and
eliminates framework complexity.

**Alternatives considered**:
- Vue.js 3 (CDN): Adds reactive binding but unnecessary for simple
  lists and forms. Could add later if needed. Rejected for now.
- React: Requires build toolchain. Rejected.

## Decision 6: Book Metadata Enrichment

**Decision**: Open Library API (genre/subjects) + Wikidata API (author
country/nationality)

**Rationale**: Both APIs are free with no authentication required.
Open Library has rich subject data but no structured nationality field.
Wikidata has explicit `country of citizenship` property (P27). The two
complement each other.

**Pipeline**:
1. Search Open Library by title + author → get subjects, author key
2. Fetch Open Library author → get Wikidata ID if available
3. Query Wikidata by ID (or search by name) → extract P27 (country)

**Key details**:
- Open Library rate limit: 3 req/sec with User-Agent header
- Wikidata: no explicit rate limit, use User-Agent
- Cache results to avoid re-fetching for known books
- Mark enriched data as `metadata_source: 'openlibrary'/'wikidata'`

## Decision 7: Build & Deployment

**Decision**: Maven build, docker-compose deployment

**Rationale**: Maven is the standard Java build tool. Docker-compose
with two services (app + postgres) satisfies the constitution's
deployment requirement with minimal complexity.

**Key details**:
- Maven shade plugin produces single fat JAR
- Docker image: `eclipse-temurin:21-jre-alpine` + Tesseract
- Two containers: app (with Tesseract) + postgres:16-alpine
- Photo storage: Docker volume mounted to app container
- Config via environment variables (DB credentials, Telegram API keys,
  app password)
