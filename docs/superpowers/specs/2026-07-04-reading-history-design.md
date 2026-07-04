# Reading History Design

## Goal

Add server-backed reading history to the Legado web server and web frontend. Users can open a saved reading history item, delete one history item, or clear all reading history without deleting books or cached chapters.

## Scope

This feature treats a "history item" as one book's saved reading progress row. It does not add paragraph-level bookmarks or annotations inside chapter content.

In scope:

- List reading history from the server.
- Open a history item at its saved chapter index and position.
- Delete one reading history item.
- Clear all reading history.
- Keep the frontend-generated static assets embedded in `legado-server/src/main/resources/web/` in sync with `modules/web/dist/`.

Out of scope:

- Paragraph bookmarks inside a chapter.
- Cross-device user accounts.
- Separate history retention limits.
- Android app behavior changes.

## Current Context

The standalone server already stores progress in SQLite:

- `legado-server/src/main/kotlin/io/legado/server/data/Database.kt` defines `ReadProgress`.
- `BookRepository.saveReadProgress` writes progress keyed by `bookUrl`.
- `BookRepository.getAllBooks` maps progress fields back onto `Book`.
- `BookService.saveProgress` receives `/saveBookProgress`.

The web frontend currently has a single local `readingRecent` item in `BookShelf.vue`. It persists that item in browser storage and uses it as a fallback when session storage is missing.

## Recommended Approach

Use the existing `read_progress` table as the reading history source. A history item exists when a book has saved read progress, especially a positive `durChapterTime`. The server returns books with progress ordered by `durChapterTime` descending.

This avoids a new table while matching how the current reader already saves progress. Deleting history means deleting the matching `read_progress` row only. The book remains on the shelf, and cached chapters remain available.

## API Design

Add three endpoints to `ProgressRoutes.kt`:

- `GET /getReadingHistory`
- `POST /deleteReadingHistory`
- `POST /clearReadingHistory`

Responses continue to use `ReturnData`.

`GET /getReadingHistory` returns `ReturnData<List<Book>>`. Each book includes existing progress fields:

- `durChapterIndex`
- `durChapterPos`
- `durChapterTime`
- `durChapterTitle`

`POST /deleteReadingHistory` accepts:

```json
{"bookUrl": "/path/to/book.txt"}
```

It removes that book's row from `read_progress` and returns success even when no row existed.

`POST /clearReadingHistory` removes all rows from `read_progress` and returns the number of deleted rows.

## Server Data Design

Extend `BookRepository` with focused progress helpers:

- `getReadingHistory(): List<Book>`
- `deleteReadProgress(bookUrl: String): Int`
- `clearReadProgress(): Int`

`getReadingHistory()` should query books with matching progress and order by `ReadProgress.durChapterTime DESC`. It should not include books with no progress row. If a legacy or malformed progress row has `durChapterTime` equal to zero, keep it at the bottom rather than special-casing it away.

Extend `BookService` with thin methods that call the repository helpers:

- `getReadingHistory(): List<Book>`
- `deleteReadingHistory(bookUrl: String): Int`
- `clearReadingHistory(): Int`

No schema migration is needed because the feature reuses the existing table.

## Frontend Design

Update `modules/web/src/views/BookShelf.vue`:

- Replace the single "最近阅读" tag with a compact "阅读历史" list.
- Render the newest server history items first.
- Keep each item small enough for the left navigation column.
- Each item opens the same `/chapter` route via existing `toDetail` behavior.
- Each item has a delete control for removing that one history record.
- Add a "清空" control when the history list is non-empty.
- Keep the existing `readingRecent` browser-storage fallback for resilience when the server history request fails or history is empty.

Update `modules/web/src/api/api.ts`:

- Add `getReadingHistory`.
- Add `deleteReadingHistory`.
- Add `clearReadingHistory`.

Update `modules/web/src/book.d.ts`:

- Add `ReadingHistoryDeleteRequest = { bookUrl: string }` for deleting one history item.

## UX Details

When the history list has server items, show those items. When the list is empty but `readingRecent` exists, show that one fallback item. When both are empty, show "尚无阅读记录".

Deleting one item:

- Confirm is not required for single-item delete.
- Stop event propagation so the item does not open while deleting.
- Remove it from local UI state after server success.
- If the deleted item matches `readingRecent`, remove `readingRecent` from browser storage.

Clearing all items:

- Ask for confirmation before clearing.
- Remove all server history items after success.
- Remove `readingRecent` browser storage.

Use concise visible labels:

- Section title: `阅读历史`
- Empty item: `尚无阅读记录`
- Clear action: `清空`

## Error Handling

Server:

- Missing `bookUrl` in `deleteReadingHistory` returns an error `ReturnData`.
- Repository delete helpers return counts but do not fail when the row is absent.
- Unexpected exceptions are caught by the route and returned through `ReturnData.error`.

Frontend:

- If loading history fails, keep the old local `readingRecent` fallback.
- If deleting one item fails, show the returned error and keep the item visible.
- If clearing fails, show the returned error and keep the list visible.
- Use existing storage-safe helpers for local storage updates.

## Testing Plan

Server tests:

- Repository/service test creates two books with progress and verifies `getReadingHistory()` orders newest first.
- Repository/service test deletes one progress row and verifies the book remains but progress is absent.
- Repository/service test clears all progress rows and verifies books remain.
- Route test verifies the three endpoints return JSON and mutate history correctly.

Frontend tests:

- Add a static script check that `BookShelf.vue` loads reading history from the API.
- Check that single delete calls the delete API with `bookUrl`.
- Check that clear all calls the clear API.
- Check that the UI keeps the local `readingRecent` fallback.

Full verification after implementation must follow `AGENTS.md`:

```bash
cd modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
npm run build-only

cd /home/allen/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/

cd legado-server
./gradlew test shadowJar

cd /home/allen/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
```

Because this checkout is at `/run/media/allen/500G/projects/legado`, use that path for local commands while preserving the same command order.

## Acceptance Criteria

- The bookshelf page shows reading history from the server.
- A history item opens the book at its saved chapter and position.
- Deleting one history item removes only that progress record.
- Clearing history removes all progress records.
- Books and cached chapters are not deleted by history deletion.
- Frontend source changes are rebuilt into `legado-server/src/main/resources/web/`.
- Server tests and required frontend checks pass.
