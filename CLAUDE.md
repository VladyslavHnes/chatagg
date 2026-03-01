# chatagg Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-17

## Active Technologies

- Java 21 (Temurin) + Javalin 6.7.0, TDLight-Java 3.4.4, Tess4J 5.16.0, HikariCP 5.1.0, Flyway 10.8.0, PostgreSQL JDBC 42.7.3 (001-telegram-book-aggregator)

## Project Structure

```text
src/
tests/
```

## Commands

```bash
# Start the app locally (from repo root):
cd backend
set -a; source ../.env; set +a
mvn compile exec:java -Dexec.mainClass=com.chatagg.App

# Build the fat JAR:
cd backend && mvn package -DskipTests
```

> **Important:** `exec:java` does NOT auto-load `.env`. The `set -a; source ../.env; set +a` step is required — without it, `TELEGRAM_API_ID` defaults to `0` and the sync will always fail with "Failed to connect to Telegram".

## Code Style

Java 21 (Temurin): Follow standard conventions

## Recent Changes

- 001-telegram-book-aggregator: Added Java 21 (Temurin) + Javalin 6.7.0, TDLight-Java 3.4.4, Tess4J 5.16.0, HikariCP 5.1.0, Flyway 10.8.0, PostgreSQL JDBC 42.7.3

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
