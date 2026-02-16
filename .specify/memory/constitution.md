<!--
  Sync Impact Report
  ==================
  Version change: N/A → 1.0.0 (initial ratification)
  Modified principles: N/A (first version)
  Added sections:
    - Core Principles (5): Telegram as Source of Truth, Data Pipeline Integrity,
      API-First Design, Simplicity, Test-First
    - Technology Constraints
    - Development Workflow
    - Governance
  Removed sections: N/A
  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ no changes needed (generic)
    - .specify/templates/spec-template.md ✅ no changes needed (generic)
    - .specify/templates/tasks-template.md ✅ no changes needed (generic)
    - .claude/commands/*.md ✅ no changes needed (generic)
  Follow-up TODOs: None
-->

# Chatagg Constitution

## Core Principles

### I. Telegram as Source of Truth

The Telegram channel is the single, authoritative source for all book
and quote data. The system MUST NOT allow manual creation or editing
of books/quotes outside of what was posted in the channel.

- Every book entry (title, author) and every quote MUST originate
  from a Telegram channel message.
- The ingestion pipeline MUST process the full channel history on
  initial sync and handle incremental updates thereafter.
- Photo messages containing text MUST be processed through OCR to
  extract quote content. The extracted text MUST be stored alongside
  the original photo reference.
- If a Telegram message is ambiguous or OCR confidence is low, the
  system MUST flag the entry for manual review rather than silently
  discard or guess.

### II. Data Pipeline Integrity

Data flowing from Telegram to the website MUST be accurate,
complete, and traceable.

- Each stored record MUST reference the original Telegram message ID
  for traceability.
- Book metadata (genre, author country) MAY be enriched from
  external sources (e.g., Open Library API) but MUST be clearly
  marked as derived data vs. user-posted data.
- Reading duration statistics MUST be computed from Telegram message
  timestamps (first mention to last mention of a book). The
  calculation method MUST be documented and consistent.
- Duplicate detection MUST be in place: the same book posted
  multiple times MUST NOT create duplicate entries.

### III. API-First Design

All functionality MUST be exposed through a well-defined API before
any frontend is built.

- The backend MUST expose a REST API that the frontend consumes.
  No server-side rendering of dynamic content.
- Every API endpoint MUST have a documented contract (request/
  response schema) before implementation begins.
- Search functionality (quotes, books) MUST be an API capability,
  not a frontend-only filter.
- The Telegram ingestion pipeline and the web-serving API MUST be
  logically separated concerns, even if deployed together.

### IV. Simplicity (YAGNI)

Build only what is needed now. Avoid speculative abstractions.

- Start with the simplest solution that satisfies the requirement.
  Add complexity only when a concrete need arises.
- No abstraction layers, design patterns, or frameworks SHOULD be
  introduced unless they solve a demonstrated problem.
- Prefer flat, readable code over clever indirection. Three similar
  lines of code are better than a premature helper.
- External dependencies MUST be justified: each dependency MUST
  solve a problem that would take significant effort to implement
  in-house (e.g., OCR engine, Telegram client library).

### V. Test-First Development

Tests MUST be written before implementation for all non-trivial
logic.

- The Red-Green-Refactor cycle MUST be followed: write a failing
  test, implement the minimum code to pass, then refactor.
- OCR text extraction, book/quote parsing, and search indexing
  MUST have unit tests with known input/output fixtures.
- Integration tests MUST cover: Telegram API interaction (mocked),
  database persistence round-trips, and API endpoint contracts.
- Test data MUST include edge cases: Cyrillic/non-Latin text in
  quotes, photos with poor lighting, books with multiple authors.

## Technology Constraints

- **Language**: Java 18+
- **Telegram Integration**: Telegram Bot API or TDLib for channel
  history access
- **OCR Engine**: Tesseract OCR or cloud-based alternative (e.g.,
  Google Cloud Vision). The choice MUST support multilingual text.
- **Database**: Relational database (PostgreSQL preferred) for
  structured book/quote data. Full-text search MUST be supported
  natively or via a search index (e.g., PostgreSQL full-text
  search, Elasticsearch).
- **Frontend**: Static site or SPA consuming the backend API.
  Framework choice is open but MUST remain a separate build
  artifact from the backend.
- **Deployment**: MUST be deployable as a single docker-compose
  stack for local development.

## Development Workflow

- Features are developed on feature branches and merged via pull
  request.
- Each pull request MUST pass all existing tests before merge.
- Database schema changes MUST use versioned migrations (never
  manual DDL).
- API contract changes MUST be reviewed for backward compatibility.
  Breaking changes MUST increment the API version.
- Commits MUST be atomic: one logical change per commit with a
  descriptive message.

## Governance

- This constitution supersedes all ad-hoc practices. When in doubt,
  refer to these principles.
- Amendments MUST be documented with a version bump, rationale, and
  updated date. Changes to principles require MAJOR version bump;
  new sections require MINOR; wording clarifications require PATCH.
- All code reviews MUST verify compliance with these principles.
  Non-compliance MUST be justified in writing and approved before
  merge.
- Complexity beyond what these principles prescribe MUST be
  justified in the plan document's Complexity Tracking table.
- Use CLAUDE.md for runtime development guidance that supplements
  (but does not override) this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-02-16 | **Last Amended**: 2026-02-16
