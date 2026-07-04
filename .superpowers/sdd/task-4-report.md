# Tasks 3 and 4 Combined Report

## Scope

Implemented Task 3 and Task 4 together in one frontend unit without creating the intentionally partial commit state from Task 3.

Files changed:

- `AGENTS.md`
- `modules/web/package.json`
- `modules/web/scripts/check-reading-history.mjs`
- `modules/web/src/book.d.ts`
- `modules/web/src/api/api.ts`
- `modules/web/src/views/BookShelf.vue`

## What Changed

### Task 3: Frontend API types and guard

- Added `ReadingHistoryDeleteRequest` to `modules/web/src/book.d.ts`.
- Added `API.getReadingHistory()`, `API.deleteReadingHistory(request)`, and `API.clearReadingHistory()` to `modules/web/src/api/api.ts`.
- Added `modules/web/scripts/check-reading-history.mjs`.
- Added `npm run test:reading-history` to `modules/web/package.json`.
- Added `npm run test:reading-history` to the frontend verification sequence in `AGENTS.md`.

### Task 4: Bookshelf reading history UI

- Replaced the old "最近阅读" tag block with a reading-history list UI in `BookShelf.vue`.
- Added:
  - `readingHistory: Ref<Book[]>`
  - `readingHistoryItems: ComputedRef<ReadingHistoryItem[]>`
  - `loadReadingHistory()`
  - `deleteReadingHistory(item)`
  - `clearReadingHistory()`
- Preserved the local `readingRecent` fallback when the server history is empty.
- Preserved chapter-route query handling through `getChapterHref()` / `getChapterQuery()`.
- Added single-item delete and clear-all controls using existing Element Plus icons/buttons.
- Clearing/deleting matching history also clears the local `readingRecent` fallback from storage.

## RED Evidence

### RED 1: Guard before API/UI implementation

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
```

Result: failed as expected.

Key output:

```text
book.d.ts must define ReadingHistoryDeleteRequest with bookUrl.
api.ts must expose getReadingHistory returning Book[].
api.ts must expose deleteReadingHistory with ReadingHistoryDeleteRequest.
api.ts must expose clearReadingHistory returning deleted count.
BookShelf must keep server reading history in a typed ref.
BookShelf must load reading history from the server.
BookShelf must delete one reading history item by bookUrl.
BookShelf must clear all reading history through the API.
BookShelf single-item delete control must not open the book while deleting.
```

### RED 2: After Task 3 API/type work, before BookShelf wiring

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
```

Result: failed as expected, now only on `BookShelf.vue`.

Key output:

```text
BookShelf must keep server reading history in a typed ref.
BookShelf must load reading history from the server.
BookShelf must delete one reading history item by bookUrl.
BookShelf must clear all reading history through the API.
BookShelf single-item delete control must not open the book while deleting.
```

## GREEN Evidence

### Reading history guard

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
```

Result: passed.

### TypeScript

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run type-check
```

Result: passed.

### Focused frontend guards

Commands:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
```

Result: all passed.

## Small Adjustments Beyond the Brief

- To keep the existing mobile activation guard green while still rendering the new history UI, the local fallback branch keeps a native `:href="getChapterHref(readingRecent)"` path instead of routing every case through a generic history item object.
- The server-history branch still uses `ReadingHistoryItem` objects, so the route-query preservation and delete actions stay aligned with the task brief.

## Commit

Created one combined commit for Tasks 3 and 4:

```text
feat: add bookshelf reading history controls
```

## Task 4 Follow-up Fix

- Fixed the backend URL switch success path in `modules/web/src/views/BookShelf.vue` so it now refreshes server reading history alongside `store.loadBookShelf()` after `setApiEntryPoint(...)`.
- Added a static regression guard in `modules/web/scripts/check-reading-history.mjs` that inspects the `setLegadoRetmoteUrl` block and fails if a backend switch no longer triggers a reading-history refresh.

### RED

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
```

Result: failed as expected before the fix.

Key output:

```text
BookShelf backend URL changes must refresh reading history after switching the API entry point.
```

### GREEN

After wiring `loadReadingHistory()` into the backend-switch success path, the same guard passed and the full required frontend verification suite stayed green.

## Task 4 Review Fix: Clear stale history on non-success load failure

- Updated `loadReadingHistory()` in `modules/web/src/views/BookShelf.vue` to clear `readingHistory.value` before reporting a non-throwing API load failure, so backend switches cannot leave stale server history visible.
- Extended `modules/web/scripts/check-reading-history.mjs` with a regression guard that requires the `isSuccess === true` success branch and a `readingHistory.value = []` clear before the non-success `ElMessage.error(...)` path.

### RED

Command:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
```

Result: failed as expected before the fix.

Key output:

```text
BookShelf must clear stale server reading history before reporting a non-success load failure.
```

### GREEN

Commands:

```bash
cd /run/media/allen/500G/projects/legado/.worktrees/reading-history/modules/web
npm run test:reading-history
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
```

Result: all passed after the fix.
