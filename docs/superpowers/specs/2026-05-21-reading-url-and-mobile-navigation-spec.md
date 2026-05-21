# WSL2 Reading Server and Mobile Web Development Spec

**Owner:** Allen
**Date:** 2026-05-21
**Branch:** `wsl2`
**Base commit:** `40647f528` (`master`, `origin/master`)
**Documented range:** `master..wsl2`

## Purpose

This document records the full `wsl2` branch development line from the branch point through the latest implementation commit before this document update. It covers the local Legado server, deployment documentation, TTS integration, mobile browser reliability fixes, browser-history routing, token-preserving chapter URLs, and mobile chapter tap hotspots.

The work began by adding a JVM/Ktor reading server for WSL2/Linux and evolved into a mobile-friendly external browser reading workflow behind Nginx token authentication.

## Branch Commit Chronology

The `wsl2` branch contains these commits after `40647f528`:

| Commit | Summary | Development Record |
| ------ | ------- | ------------------ |
| `52d8dfbc1` | 新增 legado-server: WSL2/JVM 本地書籍閱讀服務 | Added the standalone `legado-server` Ktor/JVM service for reading local books through a browser. |
| `5a2497bac` | 新增 legado-server 部署文件: Ubuntu 22.04 部署指南 | Added Ubuntu deployment guidance for the server service. |
| `50595ea2d` | 新增項目文檔: 開發指南與架構分析 | Added project development and architecture documentation. |
| `388ea6a27` | 新增 TTS 朗讀功能：Kokoro-FastAPI 整合 | Integrated Kokoro-FastAPI TTS playback support. |
| `98f06a93a` | TTS 朗讀完自動跳轉下一章繼續播放 | Added TTS auto-advance to the next chapter after playback finishes. |
| `0a0ed8ef9` | Fix mobile web bookshelf selection | Fixed mobile bookshelf selection behavior. |
| `a192c079e` | Fix mobile reading state fallback | Added mobile-safe reading-state recovery fallbacks. |
| `677dc0eab` | Fix mobile book tap activation | Improved mobile book tap activation in scrollable lists. |
| `a06729f84` | Add native chapter links for mobile | Added native anchor chapter links for mobile navigation fallback. |
| `2d57e6942` | Add native recent reading link | Added native href support for recent-reading entry. |
| `6b5713c31` | Preserve native mobile chapter navigation | Preserved native mobile navigation instead of blocking it with JavaScript-only routing. |
| `41af48697` | Stop serving txt files as covers | Prevented TXT book content from being served as cover images. |
| `cc5ed7da6` | Document frontend packaging workflow | Documented required frontend build and server-resource embedding workflow. |
| `1bdb1df3b` | Require Allen in assistant responses | Added the agent response rule requiring the name "Allen". |
| `cbc75e35d` | Sync chapter progress to route query | Synchronized chapter progress into route query while reading. |
| `50a5f9a5c` | Use browser history for chapter URLs | Replaced hash routing with browser-history routing and added server SPA fallback. |
| `9436ad5c6` | Document JAR reupload requirement | Documented that changed JARs must be rebuilt and re-uploaded before deployment. |
| `14b363cb3` | Add Codex repository guidance | Added Codex guidance files for repository and server work. |
| `019bec4b3` | Add mobile chapter tap hotspots | Added mobile bottom-corner previous/next chapter tap hotspots. |
| `c75388a49` | Document reading URL and mobile navigation spec | Added the first version of this development specification. |

## Development Areas

### WSL2 Local Reading Server

The branch introduced `legado-server`, a JVM-only local reading service intended for WSL2/Linux deployment. The service provides browser access to local TXT/EPUB books and exposes Legado-compatible web APIs for bookshelf, catalog, chapter content, reading progress, read config, cover, and TTS-related routes.

Core characteristics:

- Ktor 3.0.0 with Netty.
- SQLite storage through Exposed.
- Local filesystem book scanning.
- TXT and EPUB parsing.
- Static Vue frontend served from embedded resources under `legado-server/src/main/resources/web/`.
- Standalone fat JAR produced by `legado-server`'s `shadowJar` task.

### Deployment and Project Documentation

The branch added deployment and repository guidance for operating the WSL2 server, including Ubuntu service setup, server architecture notes, and Codex/agent instructions.

Important operational rules established on the branch:

- The frontend must be built from `modules/web/`.
- `modules/web/dist/` must be copied into `legado-server/src/main/resources/web/`.
- `./gradlew test shadowJar` must be run from `legado-server/`, not from the repository root.
- Generated embedded frontend assets must be committed with frontend source changes.
- If code changes after a JAR was uploaded, rebuild and upload the JAR again before installation.

### TTS Reading

The branch integrated TTS support through Kokoro-FastAPI and added auto-advance behavior when a chapter finishes playback.

Key behavior:

- `TtsPlayer.vue` provides playback controls.
- Server TTS routes proxy speech/voice requests.
- Reading view tracks TTS state.
- When TTS finishes a chapter and another chapter exists, the reader loads the next chapter and continues playback.

### Mobile Browser Reliability

Several commits focused on mobile browser behavior, especially mobile Brave and external access through Nginx.

Implemented reliability improvements:

- Mobile bookshelf selection no longer depends only on fragile click behavior.
- Reading state can be recovered from route query, session storage, Pinia state, and recent-reading local storage.
- Book rows and recent-reading entries have native hrefs so mobile browsers can navigate even if JavaScript click handling is unreliable.
- Native navigation is intentionally preserved for mobile fallback.
- Cover serving avoids returning TXT book bytes as image data.

### Browser URL and Routing

The later branch work addressed the problem that hash-router chapter state is not visible to Nginx or backend logs and can be awkward for reload/bookmark behavior.

The implemented route behavior is:

- Vue Router uses browser history instead of hash history.
- Chapter links use `/chapter?...` instead of `#/chapter?...`.
- Server fallback returns the Vue app for direct `/chapter`, `/bookSource`, and `/rssSource` loads.
- Route query is the source for current book/chapter recovery when present.

### Mobile Chapter Hotspots

The newest feature adds mobile-only transparent previous/next chapter tap zones to avoid opening the toolbar just to change chapter.

Behavior:

- Bottom-left transparent hotspot triggers previous chapter.
- Bottom-right transparent hotspot triggers next chapter.
- The center tap area still toggles the toolbar.
- Hotspots use `@click.stop` so they do not toggle the toolbar.
- Hotspots render only in mobile `miniInterface` mode.

## Goals

- Provide a working WSL2/JVM local book reading server.
- Serve local books through a browser using Legado-compatible API shapes.
- Support local deployment as a systemd service.
- Support TTS playback and chapter auto-advance.
- Make mobile browser bookshelf and chapter activation reliable.
- Use real browser-history URLs such as `/chapter?...` instead of `#/chapter?...`.
- Preserve Nginx/token authentication query parameters while updating reading-state query parameters.
- Let direct browser loads of `/chapter`, `/bookSource`, and `/rssSource` return the Vue app.
- Keep native mobile anchor fallback behavior for unreliable mobile click activation.
- Add invisible bottom-left and bottom-right mobile reading tap zones for previous/next chapter.
- Rebuild and embed frontend assets into the server JAR whenever frontend code changes.
- Re-upload the rebuilt JAR whenever code changes after a previous upload.

## Non-Goals

- This does not redesign the reading UI toolbar.
- This does not add page-turn animation.
- This does not change chapter loading semantics, catalog parsing, or book progress persistence APIs.
- This does not modify Nginx configuration directly; it only documents that public real-path routing must be verified through Nginx after deployment.
- This does not merge `wsl2` back to `master`; it records the branch work.

## Implemented Behavior

### Browser History Routing

All Vue Router instances now use `createWebHistory()`:

- `modules/web/src/router/index.ts`
- `modules/web/src/router/bookRouter.ts`
- `modules/web/src/router/sourceRouter.ts`

The app now expects URLs like:

```text
/chapter?token=<token>&bookUrl=<encoded-url>&bookName=<name>&chapterIndex=54&chapterPos=0
```

Hash URLs such as `#/chapter?...` are no longer generated for chapter links.

### Chapter Query Serialization

`modules/web/src/utils/chapterLink.ts` centralizes chapter URL/query generation.

Reading-state query keys are controlled by the selected/current book:

- `bookUrl`
- `bookName`
- `bookAuthor`
- `chapterIndex`
- `chapterPos`
- `isSeachBook`

Non-reading query keys are preserved. This is important for public access through Nginx token authentication. For example, if the current URL contains `token=abc`, chapter links and progress-sync URLs must keep `token=abc`.

The helper behavior is:

- `getChapterHref(book, preservedQuery)` returns `/chapter?...` for native anchors.
- `getChapterQuery(book, preservedQuery)` returns a Vue Router query object.
- `getPreservedRouteQuery(query)` removes reading keys and keeps non-reading keys.
- If no `preservedQuery` is passed, helpers fall back to `window.location.search` in the browser.

### Shelf and Chapter Navigation

`BookShelf.vue` passes `route.query` into `getChapterQuery()` before routing to `/chapter`, so token and other non-reading params survive shelf-to-chapter navigation.

`BookChapter.vue` uses `getChapterQuery(store.readingBook, route.query)` when synchronizing progress into the current URL.

`BookChapter.vue` uses `getPreservedRouteQuery(route.query)` when returning to `/`, so token and other non-reading params survive chapter-to-shelf navigation. This avoids the regression where a user could enter through `/chapter?token=...`, return to the shelf, then lose the token before opening another book.

### Server SPA Fallback

`legado-server/src/main/kotlin/io/legado/server/Application.kt` now exposes:

```kotlin
fun Application.configureLegadoServer(
    bookService: BookService,
    httpClient: HttpClient,
    ttsUrl: String
)
```

The production `embeddedServer` calls this function, and tests use it directly.

The server returns the embedded Vue `web/index.html` for direct loads of:

- `/chapter`
- `/bookSource`
- `/rssSource`

API routes remain registered before the frontend fallback routes and must keep returning JSON.

### Mobile Chapter Tap Hotspots

`modules/web/src/views/BookChapter.vue` adds two transparent mobile-only buttons:

- `.mobile-chapter-hotspot.previous` at the bottom-left calls `toPreChapter`.
- `.mobile-chapter-hotspot.next` at the bottom-right calls `toNextChapter`.

The hotspots:

- Render only when `miniInterface` is true.
- Use `@click.stop` so tapping them does not toggle the toolbar.
- Are transparent and fixed to the bottom corners.
- Use `touch-action: manipulation`.
- Stay below the toolbar layer (`z-index: 90`) so visible toolbar controls remain clickable.
- Are hidden on desktop layouts with `@media screen and (min-width: 777px)`.

The center of the page still keeps the existing behavior: tap to show or hide the toolbar.

## File Responsibilities

### Frontend Runtime

- `modules/web/src/router/index.ts`
  - Main route composition with browser-history routing.

- `modules/web/src/router/bookRouter.ts`
  - Bookshelf/chapter routes with browser-history routing.

- `modules/web/src/router/sourceRouter.ts`
  - Source editor routes with browser-history routing.

- `modules/web/src/utils/chapterLink.ts`
  - Shared chapter href/query serialization and non-reading query preservation.

- `modules/web/src/views/BookShelf.vue`
  - Preserves current route query when opening a book programmatically.

- `modules/web/src/views/BookChapter.vue`
  - Synchronizes chapter progress to route query.
  - Preserves token query when returning to shelf.
  - Provides mobile bottom-corner chapter tap hotspots.

### Frontend Guards

- `modules/web/scripts/check-history-router.mjs`
  - Verifies browser-history routing and chapter query preservation.

- `modules/web/scripts/check-mobile-chapter-hotspots.mjs`
  - Verifies mobile previous/next hotspots and package script wiring.

- `modules/web/scripts/check-mobile-book-activation.mjs`
  - Verifies native href fallback and mobile tap activation behavior.

- `modules/web/scripts/check-reading-state-resilience.mjs`
  - Verifies reading-state recovery and route progress synchronization.

- `modules/web/package.json`
  - Exposes `test:history-router` and `test:mobile-chapter-hotspots`.

### Server Runtime and Tests

- `legado-server/src/main/kotlin/io/legado/server/Application.kt`
  - Extracts testable server configuration.
  - Adds exact frontend fallback routes for real browser-history paths.

- `legado-server/src/test/kotlin/io/legado/server/ApplicationRoutingTest.kt`
  - Verifies `/chapter`, `/bookSource`, and `/rssSource` direct loads.
  - Verifies API routes are still JSON and not swallowed by SPA fallback.

### Generated Server Resources

- `legado-server/src/main/resources/web/`
  - Contains the built frontend copied from `modules/web/dist/`.
  - Must be committed with frontend source changes because the server serves embedded resources from the JAR.

### Agent Instructions

- `AGENTS.md`
  - Records the required frontend packaging sequence.
  - Includes `npm run test:history-router`.
  - Includes `npm run test:mobile-chapter-hotspots`.
  - Requires rebuilding and re-uploading the JAR after any code or embedded frontend change.

## Verification Commands

Run from `modules/web/`:

```bash
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
npm run build-only
```

Run from repository root:

```bash
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/
```

Run from `legado-server/`:

```bash
./gradlew test shadowJar
```

Run from repository root:

```bash
sha256sum legado-server/build/libs/legado-server-all.jar
diff -qr modules/web/dist legado-server/src/main/resources/web
jar tf legado-server/build/libs/legado-server-all.jar | rg 'web/(index\.html|assets/(BookChapter|BookShelf|index)-)'
```

## Deployment Rules

After producing a new JAR, upload it to `800g4`:

```bash
scp legado-server/build/libs/legado-server-all.jar allen@800g4:/tmp/legado-server-all.jar.new
ssh allen@800g4 'sha256sum /tmp/legado-server-all.jar.new'
```

The remote `/tmp` checksum must match the local checksum before installing.

If any code or embedded frontend resource changes after a JAR was already uploaded, rebuild the JAR and upload it again. Do not install an older `/tmp/legado-server-all.jar.new`.

Install and restart on `800g4`:

```bash
sudo install -o legado -g legado -m 0644 /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar
sudo systemctl restart legado-server
sha256sum /opt/legado-server/legado-server-all.jar
systemctl status legado-server --no-pager -l
```

## Acceptance Criteria

- Mobile Brave can open a book from shelf/search and read content through external Nginx/token access.
- Chapter URLs appear as real paths and query strings before any hash fragment.
- Browser reload/bookmark behavior can recover the current book and chapter from `/chapter?...`.
- Nginx logs can show real `/chapter?...chapterIndex=...` requests when the browser navigates to chapter routes.
- Returning from chapter to shelf preserves token query parameters.
- Mobile readers can tap the bottom-right corner for next chapter and bottom-left corner for previous chapter without opening the toolbar.
- The center tap area still toggles the toolbar.
- Direct HTTP requests to `/chapter`, `/bookSource`, and `/rssSource` return HTML from the server.
- API requests such as `/getChapterList` continue returning JSON.
- The server JAR contains the latest embedded frontend assets.

## Current Built Artifact

After the mobile hotspot work, the uploaded JAR checksum was:

```text
031a1092754352b86858864847f257ad3b64ddc92363e1d48d285228901d3c8d
```

Remote upload target:

```text
allen@800g4:/tmp/legado-server-all.jar.new
```

The service still requires sudo installation and restart before this artifact becomes active.
