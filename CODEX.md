# CODEX.md

This file provides guidance to Codex when working with code in this repository.

Codex must also follow `AGENTS.md` in this directory. In particular, every
assistant response must include the name "Allen", and frontend changes under
`modules/web/` require rebuilding and copying the generated assets into
`legado-server/src/main/resources/web/` before producing or deploying the server
JAR.

## Project Overview

Legado (开源阅读) is a free, open-source Android novel reader application written
in Kotlin. The app allows users to read novels from customizable web sources
with support for local book formats (TXT, EPUB, MOBI). It does not provide
content; users must add their own book sources.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config in gradle.properties)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
```

## Architecture

### Module Structure

```text
legado/
├── app/                    # Main Android application
├── modules/
│   ├── book/               # Book-related business logic library
│   └── rhino/              # JavaScript runtime (Mozilla Rhino wrapper)
```

### Main App Package Structure (`app/src/main/java/io/legado/app/`)

- `api/` - REST API controllers and Content Provider for external access
- `base/` - Base classes for Activities, Fragments, ViewModels, and RecyclerView
  adapters
- `data/` - Room database (`AppDatabase.kt`), DAOs, and entity models
- `model/` - Business logic: rule parsing (`analyzeRule/`), local book handling,
  RSS, web book fetching
- `service/` - Background services: `AudioPlayService`, `CacheBookService`,
  `DownloadService`, `WebService`
- `ui/` - UI layer organized by feature: book reading, main bookshelf, settings,
  RSS, etc.
- `help/` - Helper classes for configuration, HTTP, crypto, Rhino JS execution,
  storage
- `lib/` - Third-party library wrappers (Cronet, WebDAV, dialogs, permissions)
- `utils/` - Kotlin extension functions and utilities

### Key Architectural Patterns

- **MVVM**: ViewModels with LiveData for state management. Base classes live in
  the `base/` package.
- **Room Database**: Strongly typed ORM with migrations in
  `data/DatabaseMigrations.kt`.
- **Coroutines**: Modern async patterns throughout, with utilities in
  `help/coroutine/`.
- **Rule-Based Parsing**: Extensible source parsing system where book sources
  are JSON definitions with XPath, Regex, and JavaScript rules. Rules are
  processed in `model/analyzeRule/`.
- **Rhino JavaScript Engine**: User-defined book sources can include JavaScript
  for complex parsing logic.

### Important Entities (`data/entities/`)

- `BookSource.kt` - Defines a web source with parsing rules
- `Book.kt` - Book metadata and reading state
- `BookChapter.kt` - Chapter information
- `ReplaceRule.kt` - Content filtering and replacement rules

### API Access

The app exposes APIs via:

1. **Web API** (NanoHTTPD) - REST endpoints when Web Service is enabled in
   settings
2. **Content Provider** - IPC via `content://[package].readerProvider/`
3. **Deep Links** - `legado://import/{type}?src={url}` for importing sources and
   rules

API documentation: see `api.md` in the repository root.

## Technology Stack

- **Language**: Kotlin 2.3.0, Java 17
- **Build**: Gradle 8.13.2, Android Gradle Plugin 8.13.2
- **Min SDK**: 21, Target SDK: 36
- **Annotation Processing**: KSP (Kotlin Symbol Processing)
- **Database**: Room 2.7.1
- **Networking**: OkHttp 5.3.2, Cronet (optional)
- **HTML Parsing**: Jsoup 1.16.2, JsoupXpath
- **JSON**: Gson, JSONPath
- **Image Loading**: Glide 5.0.5
- **Media**: AndroidX Media3 (ExoPlayer)
- **Web Server**: NanoHTTPD 2.3.1

## Version Naming

Version format: `3.[YY].[MMDD][HH]` based on build time (GMT+8).

## Notes

- The codebase is primarily in Chinese with Chinese comments.
- Signing configuration is external (`RELEASE_STORE_FILE` in `gradle.properties`).
- ProGuard rules: `proguard-rules.pro` and `cronet-proguard-rules.pro`.
- Room schemas are exported to `app/schemas/` for migration testing.
