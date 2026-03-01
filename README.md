# Chatagg

A personal Telegram book & quote aggregator. Syncs a Telegram channel of book announcements and quotes into a searchable, organized web archive with analytics.

## Features

- **Book catalog** — paginated, sortable, filterable by title, author, country, and genre
- **Quote archive** — full-text search across all quotes, including photo quotes
- **Telegram sync** — pulls channel history via TDLib, extracts books, quotes, and photos automatically
- **Statistics** — reading heatmap, books per year/month, genre and country breakdowns, top authors
- **Review queue** — admin tool to approve, dismiss, or correct ambiguous extractions
- **User management** — multi-user with admin and regular roles
- **Dark/light theme** — persisted per device

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Javalin 6.7.0 |
| Telegram | TDLight-Java 3.4.4 (TDLib) |
| Database | PostgreSQL 16, Flyway 10.8.0, HikariCP 5.1.0 |
| OCR | Tess4J 5.16.0 (Tesseract) |
| Frontend | Vanilla HTML/CSS/JS |

## Getting Started

### Prerequisites

- Java 21 (Temurin)
- Maven 3.8+
- PostgreSQL 16
- Tesseract OCR with English and Ukrainian language data

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Get from https://my.telegram.org
TELEGRAM_API_ID=your_api_id
TELEGRAM_API_HASH=your_api_hash
TELEGRAM_PHONE=+1234567890
TELEGRAM_CHANNEL_ID=-1001234567890

DB_HOST=localhost
DB_PORT=5432
DB_NAME=chatagg
DB_USER=chatagg
DB_PASSWORD=your_password

APP_PASSWORD=your_admin_password
APP_PORT=7070
```

### 2. Run

```bash
cd backend
set -a; source ../.env; set +a
mvn compile exec:java -Dexec.mainClass=com.chatagg.App
```

> **Important:** The `set -a; source ../.env; set +a` step is required. Without it, `TELEGRAM_API_ID` defaults to `0` and sync will fail.

Open [http://localhost:7070](http://localhost:7070) and log in with `admin` / `<APP_PASSWORD>`.

### 3. Sync

Click **Sync** on the books page. On first run, Telegram will prompt for a verification code — enter it in the auth modal that appears.

## Docker

```bash
docker compose up --build
```

This starts PostgreSQL and the app together. The app is available at `http://localhost:7070`.

Persistent volumes:
- `pgdata` — PostgreSQL data
- `photos` — downloaded Telegram photos
- `tdlib-session` — Telegram session state (avoids re-authentication on restart)

## Build

```bash
cd backend && mvn package -DskipTests
# Output: backend/target/chatagg-1.0.0-SNAPSHOT.jar
```

## Project Structure

```
backend/
  src/main/java/com/chatagg/
    App.java                  # Entry point, routes
    api/                      # HTTP controllers
    db/                       # DAOs (BookDao, QuoteDao, AuthorDao, ...)
    model/                    # Data models
    sync/                     # Telegram sync logic
    telegram/                 # TDLib client & message parser
    ocr/                      # Tesseract OCR service
    enrichment/               # OpenLibrary & Wikidata enrichment
  src/main/resources/db/migration/
    V1__create_tables.sql
    V2__add_search_index.sql
    V3__add_impression.sql
    V4__create_app_user.sql
    V5__add_performance_indexes.sql
    V6__clear_photo_quote_ocr_text.sql

frontend/
  *.html                      # One HTML file per page
  css/style.css               # Shared styles with CSS variables for theming
  js/                         # Page-specific JS + shared api.js, role.js, theme.js
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `TELEGRAM_API_ID` | Telegram app API ID (from my.telegram.org) | — |
| `TELEGRAM_API_HASH` | Telegram app API hash | — |
| `TELEGRAM_PHONE` | Phone number on the Telegram account | — |
| `TELEGRAM_CHANNEL_ID` | Channel ID to sync (negative number) | — |
| `DB_HOST` | PostgreSQL host | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `chatagg` |
| `DB_USER` | Database user | `chatagg` |
| `DB_PASSWORD` | Database password | — |
| `APP_PASSWORD` | Password for the default admin user | — |
| `APP_PORT` | HTTP port | `7070` |
| `TESSERACT_DATA_PATH` | Path to Tesseract language data | `/usr/share/tesseract-ocr/5/tessdata` |
| `OCR_CONFIDENCE_THRESHOLD` | Minimum OCR confidence to auto-approve (0–100) | `70` |
| `PHOTO_STORAGE_PATH` | Directory for downloaded photos | `/app/photos` |

## API Overview

```
GET  /api/books                 Paginated book list (filter by title, author, country, genre)
GET  /api/books/{id}            Book detail
GET  /api/books/{id}/quotes     Quotes for a book
GET  /api/quotes/search         Full-text quote search
GET  /api/quotes/browse         Paginated quote browse
GET  /api/quotes/random         Random approved quote
GET  /api/authors               Author list
GET  /api/authors/{id}          Author detail with books
GET  /api/stats                 Aggregated statistics

POST /api/sync                  Trigger Telegram sync (admin)
POST /api/enrich                Enrich book metadata (admin)
GET  /api/review                Flagged items queue (admin)
PUT  /api/review/{id}/approve   Approve a flagged item (admin)
PUT  /api/review/{id}/dismiss   Dismiss a flagged item (admin)
```

All API endpoints require HTTP Basic Auth. Write operations (`POST`, `PUT`, `DELETE`) require the admin role.
