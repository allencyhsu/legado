# Bookshelf Categories and Local Search Design

## Goal

Classify the Legado web server bookshelf by the existing local directory layout and author folders, then improve local bookshelf search so the 2251-book server library is easier to browse without depending on the online WebSocket search flow.

## Scope

In scope:

- Parse local book metadata from file paths and filenames during server directory scans.
- Fill the existing `Book.name`, `Book.author`, and `Book.kind` fields for local TXT/EPUB books.
- Keep using the current `books` SQLite table; no new schema is required.
- Show the bookshelf directory-first in the web UI.
- Group books by top-level directory category, then by author.
- Improve local search matching across title, author, category, filename, and path.
- Keep embedded frontend resources in `legado-server/src/main/resources/web/` in sync after frontend changes.

Out of scope:

- Online book-source search behavior and the `searchBook` WebSocket service.
- User-editable custom tags or manual category management.
- A separate admin UI for changing metadata.
- A new database table for categories or authors.
- Android app behavior changes.

## Current Context

The standalone server already has metadata fields that match this feature:

- `Book.author`
- `Book.kind`
- `Book.originName`
- `Book.bookUrl`

However, `Book.fromFile(file)` currently sets only the filename-derived `name`, `originName`, `bookUrl`, `tocUrl`, type, and timestamps. The deployed server on `800g4` runs with:

```text
/usr/bin/java -jar /opt/legado-server/legado-server-all.jar 8081 /media/4tb/Novel /opt/legado-server/data/legado.db
```

The live bookshelf currently has 2251 books, with zero nonblank `author` values and zero nonblank `kind` values. The path structure already contains the missing information, for example:

```text
/media/4tb/Novel/白金作者合集/12.愛潛水的烏賊合集/《詭秘之主》作者：愛潛水的烏賊.txt
```

The frontend bookshelf currently filters local books with:

```ts
book.name.includes(searchWord.value) || book.author.includes(searchWord.value)
```

That misses category, filename, full path, punctuation-insensitive title matches, and common simplified/traditional variants.

## Recommended Approach

Add a small server-side metadata parser used during local book import. It should derive `name`, `author`, and `kind` once, store those values in the existing `books` row, and let the frontend consume those normal fields.

Then update the web bookshelf to render a directory-first grouped view:

1. Top-level category such as `白金作者合集`, `大神作者合集`, `其他人氣作者合集`.
2. Author group inside the category, such as `愛潛水的烏賊`.
3. Existing book cards/rows inside each author group.

Local search should stay client-side for this library size. It should normalize search text and book fields, match against a broader local index, and render only matching category/author/book groups.

This avoids a second metadata model, keeps API compatibility, and makes reading history and future bookshelf features see the same cleaned book names and authors.

## Alternatives Considered

### Frontend-only path parsing

The frontend could parse `book.bookUrl` and display groups without changing the server. This has the smallest immediate blast radius, but leaves `author` and `kind` empty in the API, database, reading history, and any future consumers. It also duplicates parsing rules in the browser.

### Dedicated category API

The server could expose a separate tree-shaped endpoint for categories and authors. This makes frontend rendering direct, but adds a second representation of the same books and is heavier than needed for the current service.

### Recommended server metadata plus frontend grouping

Parsing once on import gives one consistent source of truth while keeping the API shape unchanged. The frontend can stay simple: group and search the normal `Book[]` response.

## Server Metadata Parsing

Create a focused helper for local file metadata extraction. It should be independent from `BookService` so it can be unit-tested directly.

Input:

- book file
- configured books root directory, such as `/media/4tb/Novel`

Output:

- cleaned title
- cleaned author
- category

Parsing rules:

- `kind` is the first path segment under the books root.
- The author directory is the second path segment under the books root.
- Clean author directories by removing a leading numeric prefix and punctuation, then removing a trailing `合集`.
- Prefer filename author metadata over author-directory metadata when both are present.
- Prefer filename title metadata over the raw filename when a title pattern is present.
- If a file cannot be relativized against the configured books root, fall back to current `Book.fromFile(file)` behavior.

Filename title and author patterns:

- `《書名》作者：作者`
- `《書名》作者:作者`
- `書名 作者：作者`
- `書名 作者:作者`

Examples:

| Path | Parsed name | Parsed author | Parsed kind |
| --- | --- | --- | --- |
| `/media/4tb/Novel/白金作者合集/12.愛潛水的烏賊合集/《詭秘之主》作者：愛潛水的烏賊.txt` | `詭秘之主` | `愛潛水的烏賊` | `白金作者合集` |
| `/media/4tb/Novel/其他人氣作者合集/161.西瓜是水果/人生重啟二十年.txt` | `人生重啟二十年` | `西瓜是水果` | `其他人氣作者合集` |
| `/media/4tb/Novel/其他人氣作者合集/162/翁媳乱情.txt` | `翁媳乱情` | `` | `其他人氣作者合集` |

When an existing book row is rescanned, the server should update `name`, `author`, and `kind` if the file metadata parser now derives better values. This lets the deployed database self-heal after restart or refresh without a separate migration.

## Frontend Grouping

Update the bookshelf page to transform `store.shelf` into grouped view data.

Grouping rules:

- Category key: `book.kind || '未分類'`.
- Author key: `book.author || '未知作者'`.
- Preserve the store's current book order inside author groups unless a later implementation needs a stronger sort.
- Render empty states using existing language style.

Desktop layout:

- Keep the left navigation/sidebar structure.
- The right shelf area becomes a vertical list of category sections.
- Each category section shows title and book count.
- Each author group shows author name and count, followed by existing book cards.

Mobile layout:

- Keep the one-column reading flow.
- Category and author headers should be compact and sticky behavior is not required.
- Book rows should keep the current direct tap activation behavior.

## Local Search

The search box should continue to update local results as the user types. Pressing Enter should keep the existing online-search path for compatibility, but this project does not change or repair online search.

Search normalization:

- Trim leading/trailing spaces.
- Lowercase ASCII text.
- Remove common whitespace.
- Remove common title punctuation such as `《》<>「」『』[]【】()（）:：,，.。-－_`.
- Normalize a focused set of common simplified/traditional variants found in title and author searches.

The simplified/traditional normalization should be implemented as a centralized local mapping in the frontend search helper, not as scattered replacements in the view. It should cover the examples already present in the target library and can be extended later without changing the grouping UI.

Search fields:

- `book.name`
- `book.author`
- `book.kind`
- `book.originName`
- `book.bookUrl`

Result rendering:

- If search is empty, render the full grouped bookshelf.
- If search has text, render only matching books, still grouped by category and author.
- Show a compact result count near the shelf area.
- If there are no local matches, show an empty local-result message and leave online search behavior unchanged.

## Error Handling

Server:

- Metadata parsing must not fail book import.
- On parsing failure, log at debug and use current filename fallback.
- Directory names that clean to empty should not be used as authors.
- Unsupported path depth should still import books.

Frontend:

- Missing `kind`, `author`, `originName`, or `bookUrl` fields should not break grouping or search.
- Search normalization should handle empty and undefined values.
- Group headings should not render duplicate empty labels.

## Testing Plan

Server tests:

- Parse `《詭秘之主》作者：愛潛水的烏賊.txt` under `白金作者合集/12.愛潛水的烏賊合集`.
- Parse a plain filename under an author folder such as `161.西瓜是水果/人生重啟二十年.txt`.
- Fall back safely for numeric-only author folders such as `162/翁媳乱情.txt`.
- Verify `scanBooksDirectory()` stores parsed `name`, `author`, and `kind` in `BookRepository`.
- Verify rescanning updates an existing row whose old `author` and `kind` were blank.

Frontend tests:

- Add a focused static or script-based check for grouped bookshelf rendering code.
- Check local search indexes `name`, `author`, `kind`, `originName`, and `bookUrl`.
- Check normalization handles punctuation-insensitive title queries.
- Check the existing mobile layout and activation smoke tests still pass.

Full verification after implementation must follow `AGENTS.md`:

```bash
cd modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:reading-history
npm run test:history-router
npm run test:cover-safety
npm run build-only

cd /run/media/allen/500G/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/

cd legado-server
./gradlew test shadowJar

cd /run/media/allen/500G/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
```

If deployed to `allen@800g4`, upload the rebuilt JAR, verify the remote `/tmp` checksum, install it, restart `legado-server`, and verify the new hashed frontend chunks in Nginx access logs.

## Acceptance Criteria

- After scanning `/media/4tb/Novel`, books expose meaningful `author` and `kind` values through `/getBookshelf`.
- Filenames in `《書名》作者：作者` format display clean book names instead of the full filename stem.
- The bookshelf groups books by original top-level directory category and then author.
- Local search matches title, author, category, filename, and full path.
- Local search is tolerant of common punctuation, whitespace, and selected simplified/traditional differences.
- Existing reading history, chapter navigation, cover handling, and mobile tap behavior continue to work.
- Frontend generated assets are rebuilt and copied into `legado-server/src/main/resources/web/`.
- Server tests, frontend checks, and `shadowJar` pass.
