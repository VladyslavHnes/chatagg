# Implementation Plan: Telegram Book & Quote Aggregator

**Branch**: `001-telegram-book-aggregator` | **Date**: 2026-02-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-telegram-book-aggregator/spec.md`

## Summary

Build a system that ingests a Telegram channel's full history, extracts book entries (title + author) and quotes (text + OCR from photos), stores them in PostgreSQL, and serves them through a password-protected website with browsing, search, filtering, statistics, and a review queue for flagged items.

**Technical approach**: Java 21 backend using Javalin (lightweight web framework) with TDLight-Java for Telegram channel access, Tess4J for OCR, PostgreSQL with native full-text search, and a plain HTML/CSS/JS frontend served as static files. Deployed as a two-container docker-compose stack.

## Technical Context

**Language/Version**: Java 21 (Temurin)
**Primary Dependencies**: Javalin 6.7.0, TDLight-Java 3.4.4, Tess4J 5.16.0, HikariCP 5.1.0, Flyway 10.8.0, PostgreSQL JDBC 42.7.3
**Storage**: PostgreSQL 16 with native full-text search (tsvector, GIN indexes). Photo files stored on Docker volume.
**Testing**: JUnit 5, Testcontainers (PostgreSQL), Mockito for TDLib mocking
**Target Platform**: Linux server (Docker), macOS for development
**Project Type**: Web application (Java backend + static frontend)
**Performance Goals**: Quote search <2s for 1,000 quotes; filter results <1s; sync of 100 new messages <60s (per SC-003, SC-004, SC-006)
**Constraints**: Handle channel histories of 5,000+ messages without failure (SC-007); 95% book parse accuracy (SC-001); 90% OCR success rate (SC-002)
**Scale/Scope**: Single user, ~5 web pages (book list, book detail, quote search, stats, review queue), ~7 database tables, ~12 API endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Research Gate (Phase 0)

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Telegram as Source of Truth | PASS | All books/quotes originate from Telegram messages. No manual creation. FR-013 preserves message IDs. |
| II. Data Pipeline Integrity | PASS | Every record references telegram_message_id. Metadata marked with metadata_source. Duplicate detection in FR-006. |
| III. API-First Design | PASS | OpenAPI contract defined before implementation (contracts/api.yaml). REST API with 12 endpoints. Frontend is static consumer. |
| IV. Simplicity (YAGNI) | PASS | Javalin over Spring Boot. Direct JDBC over ORM. Plain HTML/JS over React. Each dependency justified in research.md. |
| V. Test-First Development | PASS | Plan calls for JUnit 5 + Testcontainers. Fixture-based tests for parsing, OCR, search. Integration tests for API contracts. |

### Post-Design Gate (Phase 1)

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Telegram as Source of Truth | PASS | Data model has telegram_message table as the raw source. book and quote both FK to telegram_message_id. Review queue allows correction but not creation from scratch. |
| II. Data Pipeline Integrity | PASS | sync_state table tracks position. telegram_message.processing_status ensures traceability. Full-text search vector auto-updated via trigger. |
| III. API-First Design | PASS | api.yaml defines all 12 endpoints with request/response schemas before implementation. Sync, search, review all API-driven. |
| IV. Simplicity (YAGNI) | PASS | 7 tables (no unnecessary abstractions). No repository pattern - direct JDBC. No caching layer. No message queue. Two Docker containers only. |
| V. Test-First Development | PASS | Test structure includes unit/ (parsing, OCR), integration/ (DB, API), contract/ (API schema validation). Edge cases spec'd: Cyrillic text, multiple authors, blurry photos. |

**Technology Constraints compliance**:
- Java 18+: Using Java 21. PASS.
- Telegram: TDLight-Java (TDLib wrapper). PASS.
- OCR: Tess4J (Tesseract 5.x), multilingual. PASS.
- Database: PostgreSQL 16 with FTS. PASS.
- Frontend: Static files, separate from backend. PASS.
- Deployment: docker-compose stack. PASS.

**Development Workflow compliance**:
- Feature branch: `001-telegram-book-aggregator`. PASS.
- Schema migrations: Flyway. PASS.
- API contracts: api.yaml reviewed. PASS.

**No gate violations. No complexity justifications needed.**

## Project Structure

### Documentation (this feature)

```text
specs/001-telegram-book-aggregator/
├── plan.md              # This file
├── research.md          # Phase 0: technology decisions
├── data-model.md        # Phase 1: database schema
├── quickstart.md        # Phase 1: setup guide
├── contracts/
│   └── api.yaml         # Phase 1: OpenAPI contract
└── tasks.md             # Phase 2: implementation tasks (via /speckit.tasks)
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/chatagg/
│       │   ├── App.java                    # Entry point, Javalin setup
│       │   ├── config/
│       │   │   └── AppConfig.java          # Environment-based configuration
│       │   ├── model/
│       │   │   ├── Book.java
│       │   │   ├── Author.java
│       │   │   ├── Quote.java
│       │   │   ├── Photo.java
│       │   │   ├── TelegramMessage.java
│       │   │   ├── SyncState.java
│       │   │   └── ReviewItem.java
│       │   ├── db/
│       │   │   ├── DatabaseManager.java    # HikariCP + Flyway setup
│       │   │   ├── BookDao.java
│       │   │   ├── QuoteDao.java
│       │   │   ├── AuthorDao.java
│       │   │   ├── PhotoDao.java
│       │   │   ├── TelegramMessageDao.java
│       │   │   ├── SyncStateDao.java
│       │   │   └── ReviewDao.java
│       │   ├── telegram/
│       │   │   ├── TelegramClient.java     # TDLight-Java wrapper
│       │   │   └── MessageParser.java      # Book/quote extraction logic
│       │   ├── ocr/
│       │   │   └── OcrService.java         # Tess4J wrapper
│       │   ├── enrichment/
│       │   │   ├── OpenLibraryClient.java
│       │   │   └── WikidataClient.java
│       │   ├── sync/
│       │   │   └── SyncService.java        # Orchestrates full/incremental sync
│       │   └── api/
│       │       ├── AuthMiddleware.java      # HTTP Basic Auth
│       │       ├── BookController.java
│       │       ├── QuoteController.java
│       │       ├── StatsController.java
│       │       ├── SyncController.java
│       │       └── ReviewController.java
│       └── resources/
│           └── db/migration/               # Flyway SQL migrations
│               ├── V1__create_tables.sql
│               └── V2__add_search_index.sql
├── src/
│   └── test/
│       └── java/com/chatagg/
│           ├── unit/
│           │   ├── MessageParserTest.java
│           │   └── OcrServiceTest.java
│           ├── integration/
│           │   ├── BookDaoTest.java
│           │   ├── QuoteDaoTest.java
│           │   ├── SyncServiceTest.java
│           │   └── SearchTest.java
│           └── contract/
│               └── ApiContractTest.java

frontend/
├── index.html                              # Book list page
├── book.html                               # Book detail page
├── search.html                             # Quote search page
├── stats.html                              # Reading statistics page
├── review.html                             # Review queue page
├── css/
│   └── style.css
└── js/
    ├── api.js                              # Shared API client (fetch wrapper)
    ├── books.js
    ├── book-detail.js
    ├── search.js
    ├── stats.js
    └── review.js

docker-compose.yml
Dockerfile
.env.example
```

**Structure Decision**: Web application layout selected. Backend is a Maven project producing a fat JAR. Frontend is static HTML/CSS/JS served by Javalin from the `frontend/` directory. The two are separate build artifacts (frontend has no build step). This satisfies the constitution requirement that frontend and backend are separate concerns while keeping deployment simple.

## Complexity Tracking

> No gate violations detected. No complexity justifications needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none)    | —          | —                                   |
