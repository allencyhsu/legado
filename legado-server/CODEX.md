# CODEX.md

This file provides guidance to Codex when working with code in this repository.

Codex must also follow the repository-root `AGENTS.md`. In particular, frontend
changes under `modules/web/` must be rebuilt and copied into
`legado-server/src/main/resources/web/` before producing or deploying the server
JAR, because the server serves embedded resources rather than `modules/web/dist`
directly.

## Project Overview

Legado Server is a lightweight Kotlin/JVM service that provides a web-based
interface for reading local e-books (TXT, EPUB). It runs on WSL2/Linux and
serves content through a browser, reusing the Vue.js frontend from the main
Legado Android app.

## Build Commands

```bash
# Build the project
./gradlew build

# Build fat JAR (includes all dependencies)
./gradlew shadowJar

# Clean build
./gradlew clean build

# Run tests
./gradlew test
```

Important: run these commands from `legado-server/`. The `shadowJar` task belongs
to this standalone Gradle project, not the repository root.

## Run Commands

```bash
# Run the server (default: port 8080, books from /mnt/d/Books)
java -jar build/libs/legado-server-all.jar

# Run with custom port and books directory
java -jar build/libs/legado-server-all.jar 8080 /path/to/books

# Run with all options
java -jar build/libs/legado-server-all.jar <port> <books_dir> <db_path>

# Environment variables (alternative to CLI args)
PORT=8080 BOOKS_DIR=/mnt/d/Books DB_PATH=./data/legado.db java -jar build/libs/legado-server-all.jar
```

## Architecture

### Technology Stack

- **Language**: Kotlin 2.0.0, JVM 17
- **Web Framework**: Ktor 3.0.0 (Netty)
- **Database**: SQLite + Exposed ORM
- **Book Parsing**: Jsoup (HTML), epublib (EPUB)
- **Frontend**: Vue.js (pre-built, served as static assets)

### Project Structure

```text
legado-server/
├── src/main/kotlin/io/legado/server/
│   ├── Application.kt          # Entry point, Ktor configuration
│   ├── routes/
│   │   ├── BookRoutes.kt       # /getBookshelf, /getChapterList, /getBookContent
│   │   └── ProgressRoutes.kt   # /saveBookProgress, /getReadConfig
│   ├── service/
│   │   └── BookService.kt      # Business logic, book scanning
│   ├── parser/
│   │   ├── TextFileParser.kt   # TXT file parsing with chapter detection
│   │   └── EpubParser.kt       # EPUB parsing using epublib
│   ├── model/
│   │   ├── Book.kt             # Book entity (POJO)
│   │   ├── BookChapter.kt      # Chapter entity (POJO)
│   │   └── ReturnData.kt       # API response wrapper
│   ├── data/
│   │   ├── Database.kt         # SQLite + Exposed schema
│   │   └── BookRepository.kt   # Data access layer
│   └── util/
│       └── EncodingDetect.kt   # File encoding detection
└── src/main/resources/
    ├── web/                    # Vue.js frontend (static files)
    └── logback.xml             # Logging configuration
```

### API Endpoints

All endpoints return JSON in `ReturnData` format:

```json
{"isSuccess": true, "errorMsg": "", "data": "..."}
```

| Method | Endpoint | Parameters | Description |
| ------ | -------- | ---------- | ----------- |
| GET | `/getBookshelf` | - | Get all books |
| GET | `/getChapterList` | `url` (bookUrl) | Get chapter list |
| GET | `/getBookContent` | `url`, `index` | Get chapter content (HTML) |
| GET | `/refreshToc` | `url` | Refresh table of contents |
| POST | `/saveBook` | JSON body | Save/update book |
| POST | `/deleteBook` | JSON body | Delete book |
| POST | `/saveBookProgress` | JSON body | Save reading progress |
| GET | `/getReadConfig` | - | Get reading config |
| POST | `/saveReadConfig` | JSON body | Save reading config |
| GET | `/cover` | `path` | Get book cover image |

### Key Design Patterns

- **Stateless API**: Each request is independent; progress is stored in SQLite.
- **Lazy Parsing**: Chapters are parsed on first access, then cached in the DB.
- **Encoding Detection**: Automatic charset detection for TXT files (UTF-8, GBK).
- **Chapter Detection**: Regex-based chapter title detection for Chinese novels.

## Important Files

| File | Purpose |
| ---- | ------- |
| `TextFileParser.kt` | Chapter detection patterns for TXT files |
| `EpubParser.kt` | EPUB TOC extraction and content parsing |
| `BookService.kt` | Directory scanning and book management |
| `Database.kt` | SQLite schema (`Books`, `Chapters`, `ReadProgress` tables) |

## Chapter Detection Patterns

The TXT parser supports these chapter title patterns:

- `第X章` / `第X节` / `第X回` (Chinese numbered chapters)
- `Chapter X` (English chapters)
- `1.` / `2.` (numbered with dot)
- `卷X` / `篇X` (volume/part markers)
- `序章` / `引子` / `尾声` (special chapters)

## Frontend

The Vue.js frontend is pre-built and served from `src/main/resources/web/`. It is
copied from `modules/web/dist/` after building the Vue app.

To update the frontend, follow the full packaging sequence in the repository-root
`AGENTS.md`:

1. Build and test the Vue app in `modules/web/`.
2. Copy `modules/web/dist/` to `legado-server/src/main/resources/web/`.
3. Run `./gradlew test shadowJar` from `legado-server/`.
4. Verify the resulting JAR checksum.

## Database

SQLite database stored at `./data/legado.db` by default, configurable via CLI
argument or `DB_PATH`.

Tables:

- `books` - Book metadata
- `chapters` - Chapter info with byte positions
- `read_progress` - Reading progress per book

## Notes

- This is a JVM-only port of Legado's local book reading functionality.
- There are no Android dependencies; it runs on any JVM 17+ environment.
- It is designed for WSL2 but works on any Linux/macOS/Windows system with Java.
- Book files are read directly from the filesystem; no import is needed.
- Supports hot reloading: add books to the directory and they appear on refresh.
