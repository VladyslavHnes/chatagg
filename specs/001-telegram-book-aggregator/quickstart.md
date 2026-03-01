# Quickstart: Telegram Book & Quote Aggregator

## Prerequisites

- Java 17+ (Temurin recommended)
- Maven 3.9+
- Docker & Docker Compose
- Telegram account with admin access to your channel
- Telegram API credentials (api_id, api_hash) from
  https://my.telegram.org/apps

## 1. Clone & Build

```bash
git clone <repo-url>
cd chatagg
mvn clean package -DskipTests
```

## 2. Configure Environment

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Telegram
TELEGRAM_API_ID=your_api_id
TELEGRAM_API_HASH=your_api_hash
TELEGRAM_PHONE=+380XXXXXXXXX
TELEGRAM_CHANNEL_ID=-100XXXXXXXXXX

# Database
DB_HOST=postgres
DB_PORT=5432
DB_NAME=chatagg
DB_USER=chatagg
DB_PASSWORD=<generate-a-strong-password>

# App
APP_PASSWORD=<your-website-password>
APP_PORT=7070

# OCR
TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
OCR_CONFIDENCE_THRESHOLD=70

# Photo storage
PHOTO_STORAGE_PATH=/app/photos
```

## 3. Start Services

```bash
docker-compose up -d
```

This starts:
- `postgres` - PostgreSQL 16 with volume for persistence
- `app` - Chatagg application (Java + Tesseract)

## 4. First-Time Telegram Authentication

On first run, the app will prompt for a Telegram verification code
via the console logs:

```bash
docker-compose logs -f app
```

Enter the code sent to your Telegram account. This is a one-time
setup; the session is persisted in a Docker volume.

## 5. Initial Sync

Open `http://localhost:7070` in your browser. Log in with the
password from `APP_PASSWORD`. Click "Sync Now" to start the full
channel history import.

The first sync may take several minutes depending on channel size
(~5,000 messages takes approximately 2-3 minutes).

## 6. Verify

After sync completes:
- Book list should show all books from the channel
- Click a book to see its quotes and review note
- Use the search bar to find quotes
- Check the review queue for any flagged items (low OCR confidence,
  unrecognized messages)

## Development (without Docker)

```bash
# Start PostgreSQL locally
docker run -d --name chatagg-pg \
  -e POSTGRES_DB=chatagg \
  -e POSTGRES_USER=chatagg \
  -e POSTGRES_PASSWORD=devpass \
  -p 5432:5432 \
  postgres:16-alpine

# Install Tesseract
# macOS: brew install tesseract tesseract-lang
# Ubuntu: sudo apt-get install tesseract-ocr tesseract-ocr-ukr

# Run app
DB_HOST=localhost DB_PASSWORD=devpass APP_PASSWORD=devpass \
  mvn exec:java -Dexec.mainClass="com.chatagg.App"
```

## Troubleshooting

**Telegram auth fails**: Ensure api_id/api_hash are correct. Delete
the `tdlib-session` volume and re-authenticate.

**OCR returns empty text**: Verify Tesseract language packs are
installed (`ukr.traineddata`, `eng.traineddata`). Check
`TESSERACT_DATA_PATH` points to the correct directory.

**Search returns no results**: Run a sync first. Check that quotes
have `review_status = 'approved'` (flagged quotes are excluded from
search).
