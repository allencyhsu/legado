# Bookshelf Categories and Local Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Classify local Legado server books by their existing directory and author folders, then improve local bookshelf search across metadata, filenames, and paths.

**Architecture:** Add a focused Kotlin parser that derives `Book.name`, `Book.author`, and `Book.kind` from local file paths during scans, storing the result in the existing `books` table. Add focused frontend helpers that group `Book[]` by category and author and normalize local search text, then update `BookShelf.vue` to render grouped local books while preserving the existing online-search-on-Enter behavior.

**Tech Stack:** Kotlin 2.0, JVM 17, Ktor 3.0.0, Exposed SQLite, Kotlin test, Vue 3 Composition API, Pinia, Element Plus, TypeScript 5.5, Vite 5, Node smoke-test scripts, Gradle shadowJar.

## Global Constraints

- Every assistant response must include the name "Allen".
- Do not change online book-source search behavior or the `searchBook` WebSocket service.
- Do not add a new database schema or migration; reuse `Book.name`, `Book.author`, and `Book.kind`.
- Local metadata parsing must never block book import; fallback to current filename-derived behavior.
- Frontend changes under `modules/web/` require rebuilding `modules/web/dist/` and copying it into `legado-server/src/main/resources/web/`.
- Run `shadowJar` only from `legado-server/`; do not run `./gradlew shadowJar` from the repository root.
- This checkout is at `/run/media/allen/500G/projects/legado`; use that path in local commands.
- Keep `.claude/settings.local.json` untracked and untouched.
- Use test-first implementation: write each failing test/check, verify it fails, implement the smallest passing code, then rerun.

---

## File Structure

- Create: `legado-server/src/main/kotlin/io/legado/server/service/LocalBookMetadataParser.kt`
  - Owns all local path and filename metadata parsing.
  - Produces a `LocalBookMetadata` value and a `Book` copy with parsed fields.

- Create: `legado-server/src/test/kotlin/io/legado/server/service/LocalBookMetadataParserTest.kt`
  - Unit-tests path and filename parsing without touching the database.

- Create: `legado-server/src/test/kotlin/io/legado/server/service/BookServiceMetadataImportTest.kt`
  - Tests `BookService.scanBooksDirectory()` stores and refreshes parsed metadata.

- Modify: `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`
  - Uses `LocalBookMetadataParser.toBook(file, File(booksDir))` during import.
  - Refreshes stale blank metadata even when file timestamps are unchanged.

- Create: `modules/web/src/utils/bookshelfGrouping.ts`
  - Owns local search normalization, local search matching, and grouped bookshelf view-model creation.

- Create: `modules/web/scripts/check-bookshelf-categories-search.mjs`
  - Static smoke check for frontend helper, UI wiring, package script, and AGENTS packaging list.

- Modify: `modules/web/package.json`
  - Adds `test:bookshelf-categories`.

- Modify: `AGENTS.md`
  - Adds `npm run test:bookshelf-categories` to the required frontend packaging sequence.

- Modify: `modules/web/src/components/BookItems.vue`
  - Adds an `embedded` prop so grouped sections can reuse existing book cards without nested scroll containers.

- Modify: `modules/web/src/views/BookShelf.vue`
  - Separates local grouped bookshelf results from online WebSocket search results.
  - Renders category sections, author groups, local result counts, and local empty state.

- Generated after build: `modules/web/dist/`
  - Produced by `npm run build-only`.

- Modify generated assets: `legado-server/src/main/resources/web/`
  - Refreshed from `modules/web/dist/` before building the server JAR.

---

### Task 1: Add Local Book Metadata Parser

**Files:**
- Create: `legado-server/src/test/kotlin/io/legado/server/service/LocalBookMetadataParserTest.kt`
- Create: `legado-server/src/main/kotlin/io/legado/server/service/LocalBookMetadataParser.kt`

**Interfaces:**
- Consumes: existing `io.legado.server.model.Book.fromFile(file: java.io.File): Book`.
- Produces:
  - `data class LocalBookMetadata(val name: String, val author: String = "", val kind: String? = null)`
  - `object LocalBookMetadataParser`
  - `fun LocalBookMetadataParser.parse(file: File, booksRoot: File): LocalBookMetadata`
  - `fun LocalBookMetadataParser.toBook(file: File, booksRoot: File): Book`

- [ ] **Step 1: Write failing parser tests**

Create `legado-server/src/test/kotlin/io/legado/server/service/LocalBookMetadataParserTest.kt` with:

```kotlin
package io.legado.server.service

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalBookMetadataParserTest {
    @Test
    fun `parses bracketed title author and top-level category`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("白金作者合集")
            .resolve("12.愛潛水的烏賊合集")
            .resolve("《詭秘之主》作者：愛潛水的烏賊.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("詭秘之主", metadata.name)
        assertEquals("愛潛水的烏賊", metadata.author)
        assertEquals("白金作者合集", metadata.kind)
    }

    @Test
    fun `uses cleaned author directory for plain filenames`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("161.西瓜是水果")
            .resolve("人生重啟二十年.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("人生重啟二十年", metadata.name)
        assertEquals("西瓜是水果", metadata.author)
        assertEquals("其他人氣作者合集", metadata.kind)
    }

    @Test
    fun `does not treat numeric-only author directories as authors`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("162")
            .resolve("翁媳乱情.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("翁媳乱情", metadata.name)
        assertEquals("", metadata.author)
        assertEquals("其他人氣作者合集", metadata.kind)
    }

    @Test
    fun `falls back when file is outside configured books root`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val outside = Files.createTempDirectory("legado-outside-root")
            .resolve("《外部書》作者：路人.txt")
        outside.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(outside.toFile(), root.toFile())
        val book = LocalBookMetadataParser.toBook(outside.toFile(), root.toFile())

        assertEquals("《外部書》作者：路人", metadata.name)
        assertEquals("", metadata.author)
        assertNull(metadata.kind)
        assertEquals("《外部書》作者：路人", book.name)
        assertEquals("", book.author)
        assertNull(book.kind)
    }
}
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.LocalBookMetadataParserTest
```

Expected: FAIL during Kotlin compilation with unresolved reference `LocalBookMetadataParser`.

- [ ] **Step 3: Add parser implementation**

Create `legado-server/src/main/kotlin/io/legado/server/service/LocalBookMetadataParser.kt` with:

```kotlin
package io.legado.server.service

import io.legado.server.model.Book
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

data class LocalBookMetadata(
    val name: String,
    val author: String = "",
    val kind: String? = null
)

object LocalBookMetadataParser {
    private val logger = LoggerFactory.getLogger(LocalBookMetadataParser::class.java)
    private val bracketedAuthorPattern = Regex("""^《(.+?)》\s*作者[:：]\s*(.+)$""")
    private val plainAuthorPattern = Regex("""^(.+?)\s+作者[:：]\s*(.+)$""")
    private val leadingAuthorNumberPattern = Regex("""^\s*\d+[\.\-_、\s]*""")
    private val trailingCollectionPattern = Regex("""合集$""")

    fun parse(file: File, booksRoot: File): LocalBookMetadata {
        val fallbackName = file.nameWithoutExtension.trim()
        return try {
            val relativeParts = relativePathParts(file.toPath(), booksRoot.toPath())
            val kind = relativeParts.getOrNull(0)?.cleanSegment()
            val authorFromDirectory = relativeParts.getOrNull(1)?.cleanAuthorDirectory().orEmpty()
            val filenameMetadata = parseFilename(fallbackName)

            LocalBookMetadata(
                name = filenameMetadata.name.ifBlank { fallbackName },
                author = filenameMetadata.author.ifBlank { authorFromDirectory },
                kind = kind?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            logger.debug("Falling back to filename metadata for {}", file.absolutePath, e)
            LocalBookMetadata(name = fallbackName)
        }
    }

    fun toBook(file: File, booksRoot: File): Book {
        val fallback = Book.fromFile(file)
        val metadata = parse(file, booksRoot)
        return fallback.copy(
            name = metadata.name.ifBlank { fallback.name },
            author = metadata.author,
            kind = metadata.kind
        )
    }

    private fun relativePathParts(filePath: Path, rootPath: Path): List<String> {
        val root = rootPath.toAbsolutePath().normalize()
        val file = filePath.toAbsolutePath().normalize()
        require(file.startsWith(root)) {
            "File is outside books root: $file"
        }
        return root.relativize(file).map { it.toString() }
    }

    private fun parseFilename(nameWithoutExtension: String): LocalBookMetadata {
        val trimmed = nameWithoutExtension.trim()
        val match = bracketedAuthorPattern.matchEntire(trimmed)
            ?: plainAuthorPattern.matchEntire(trimmed)
        return if (match == null) {
            LocalBookMetadata(name = trimmed)
        } else {
            LocalBookMetadata(
                name = match.groupValues[1].trim(),
                author = match.groupValues[2].trim()
            )
        }
    }

    private fun String.cleanSegment(): String = trim()

    private fun String.cleanAuthorDirectory(): String {
        return trim()
            .replace(leadingAuthorNumberPattern, "")
            .replace(trailingCollectionPattern, "")
            .trim()
    }
}
```

- [ ] **Step 4: Run parser tests and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.LocalBookMetadataParserTest
```

Expected: PASS for all tests in `LocalBookMetadataParserTest`.

- [ ] **Step 5: Commit parser task**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/test/kotlin/io/legado/server/service/LocalBookMetadataParserTest.kt legado-server/src/main/kotlin/io/legado/server/service/LocalBookMetadataParser.kt
git commit -m "feat: parse local book metadata"
```

Expected: commit includes only parser source and parser tests.

---

### Task 2: Integrate Metadata Parsing Into Server Scans

**Files:**
- Create: `legado-server/src/test/kotlin/io/legado/server/service/BookServiceMetadataImportTest.kt`
- Modify: `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`

**Interfaces:**
- Consumes:
  - `LocalBookMetadataParser.toBook(file: File, booksRoot: File): Book`
  - `BookRepository.getBook(bookUrl: String): Book?`
  - `BookRepository.upsertBook(book: Book)`
- Produces:
  - `BookService.scanBooksDirectory()` stores parsed `name`, `author`, and `kind`.
  - Existing rows with blank or outdated metadata are refreshed even when the file timestamp is unchanged.

- [ ] **Step 1: Write failing scan integration tests**

Create `legado-server/src/test/kotlin/io/legado/server/service/BookServiceMetadataImportTest.kt` with:

```kotlin
package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.model.Book
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BookServiceMetadataImportTest {
    @Test
    fun `scan stores parsed title author and category`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("白金作者合集")
            .resolve("12.愛潛水的烏賊合集")
            .resolve("《詭秘之主》作者：愛潛水的烏賊.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("詭秘之主", book.name)
        assertEquals("愛潛水的烏賊", book.author)
        assertEquals("白金作者合集", book.kind)
        assertEquals(file.fileName.toString(), book.originName)
    }

    @Test
    fun `scan refreshes metadata for unchanged existing rows`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("161.西瓜是水果")
            .resolve("人生重啟二十年.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")
        val oldBook = Book.fromFile(file.toFile()).copy(
            name = "人生重啟二十年",
            author = "",
            kind = null,
            totalChapterNum = 7,
            latestChapterTitle = "舊章節",
            lastCheckTime = file.toFile().lastModified()
        )
        BookRepository.upsertBook(oldBook)

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("人生重啟二十年", book.name)
        assertEquals("西瓜是水果", book.author)
        assertEquals("其他人氣作者合集", book.kind)
        assertEquals(7, book.totalChapterNum)
        assertEquals("舊章節", book.latestChapterTitle)
    }

    @Test
    fun `scan keeps books importable when metadata has no author`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("162")
            .resolve("翁媳乱情.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("翁媳乱情", book.name)
        assertEquals("", book.author)
        assertEquals("其他人氣作者合集", book.kind)
        assertNotNull(BookRepository.getBook(file.toFile().absolutePath))
    }

    private fun initTempDatabase() {
        val dbPath = Files.createTempDirectory("legado-metadata-import-db").resolve("legado.db")
        Database.init(dbPath.toString())
    }
}
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.BookServiceMetadataImportTest
```

Expected: FAIL because scanned books still have blank `author` and `kind`, and bracketed filenames are not cleaned.

- [ ] **Step 3: Integrate parser in `BookService.importBook`**

Modify `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`.

Replace the start of `private fun importBook(file: File)` with:

```kotlin
    private fun importBook(file: File) {
        val book = LocalBookMetadataParser.toBook(file, File(booksDir))
        val existing = BookRepository.getBook(file.absolutePath)
        val metadataChanged = existing?.let {
            it.name != book.name || it.author != book.author || it.kind != book.kind
        } ?: false
        if (existing != null && existing.lastCheckTime >= file.lastModified()) {
            if (metadataChanged) {
                BookRepository.upsertBook(
                    existing.copy(
                        name = book.name,
                        author = book.author,
                        kind = book.kind,
                        originName = book.originName,
                        tocUrl = book.tocUrl,
                        lastCheckTime = file.lastModified()
                    )
                )
            }
            return
        }

        logger.info("Importing: ${file.name}")

        val chapters = parseChapters(book)
```

Keep the existing `updatedBook`, `BookRepository.upsertBook(updatedBook)`, and `BookRepository.saveChapters(...)` block after this replacement.

- [ ] **Step 4: Run scan integration tests and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.BookServiceMetadataImportTest
```

Expected: PASS for all tests in `BookServiceMetadataImportTest`.

- [ ] **Step 5: Run parser and scan tests together**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.LocalBookMetadataParserTest --tests io.legado.server.service.BookServiceMetadataImportTest
```

Expected: PASS for both metadata test classes.

- [ ] **Step 6: Commit server integration task**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/test/kotlin/io/legado/server/service/BookServiceMetadataImportTest.kt legado-server/src/main/kotlin/io/legado/server/service/BookService.kt
git commit -m "feat: store parsed local book metadata"
```

Expected: commit includes only scan integration source and tests.

---

### Task 3: Add Frontend Grouping and Local Search Helpers

**Files:**
- Create: `modules/web/src/utils/bookshelfGrouping.ts`
- Create: `modules/web/scripts/check-bookshelf-categories-search.mjs`
- Modify: `modules/web/package.json`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: `Book` from `modules/web/src/book.d.ts`.
- Produces:
  - `type BookshelfAuthorGroup = { author: string; count: number; books: Book[] }`
  - `type BookshelfCategoryGroup = { kind: string; count: number; authorGroups: BookshelfAuthorGroup[] }`
  - `function normalizeBookshelfSearch(value?: string): string`
  - `function bookMatchesLocalSearch(book: Book, query: string): boolean`
  - `function filterBookshelfBooks(books: Book[], query: string): Book[]`
  - `function groupBookshelfBooks(books: Book[]): BookshelfCategoryGroup[]`
  - `npm run test:bookshelf-categories`

- [ ] **Step 1: Write failing frontend smoke check**

Create `modules/web/scripts/check-bookshelf-categories-search.mjs` with:

```js
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const readRepo = file =>
  fs.readFileSync(path.resolve(root, '..', '..', file), 'utf8')

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

const helper = read('src/utils/bookshelfGrouping.ts')
const packageJson = read('package.json')
const agents = readRepo('AGENTS.md')

assertContains(
  helper,
  /export type BookshelfAuthorGroup = \{[\s\S]*?author: string[\s\S]*?count: number[\s\S]*?books: Book\[\][\s\S]*?\}/,
  'bookshelfGrouping.ts must export BookshelfAuthorGroup with author, count, and books.',
)

assertContains(
  helper,
  /export type BookshelfCategoryGroup = \{[\s\S]*?kind: string[\s\S]*?count: number[\s\S]*?authorGroups: BookshelfAuthorGroup\[\][\s\S]*?\}/,
  'bookshelfGrouping.ts must export BookshelfCategoryGroup with kind, count, and authorGroups.',
)

for (const field of ['name', 'author', 'kind', 'originName', 'bookUrl']) {
  assertContains(
    helper,
    new RegExp(`book\\.${field}`),
    `Local bookshelf search must index book.${field}.`,
  )
}

assertContains(
  helper,
  /const SEARCH_PUNCTUATION = \/[\s\S]*《[\s\S]*》[\s\S]*[:：][\s\S]*[_][\s\S]*\/g/,
  'Search normalization must strip common title punctuation.',
)

assertContains(
  helper,
  /const SIMPLIFIED_TRADITIONAL_GROUPS: Array<readonly string\[]> = \[[\s\S]*?愛[\s\S]*?爱[\s\S]*?詭[\s\S]*?诡[\s\S]*?\]/,
  'Search normalization must centralize simplified/traditional groups.',
)

assertContains(
  helper,
  /export const normalizeBookshelfSearch = \(value\?: string\): string => \{[\s\S]*?toLowerCase\(\)[\s\S]*?SEARCH_PUNCTUATION[\s\S]*?\}/,
  'normalizeBookshelfSearch must lowercase and strip punctuation.',
)

assertContains(
  helper,
  /export const groupBookshelfBooks = \(books: Book\[\]\): BookshelfCategoryGroup\[\] => \{[\s\S]*?book\.kind \|\| '未分類'[\s\S]*?book\.author \|\| '未知作者'[\s\S]*?\}/,
  'groupBookshelfBooks must group by kind then author with fallback labels.',
)

assertContains(
  packageJson,
  /"test:bookshelf-categories": "node scripts\/check-bookshelf-categories-search\.mjs"/,
  'package.json must expose test:bookshelf-categories.',
)

assertContains(
  agents,
  /npm run test:bookshelf-categories/,
  'AGENTS.md packaging sequence must include npm run test:bookshelf-categories.',
)

if (process.exitCode) process.exit(process.exitCode)
```

- [ ] **Step 2: Add package script and AGENTS sequence, then verify RED**

Modify `modules/web/package.json` by adding this script after `test:reading-history`:

```json
"test:bookshelf-categories": "node scripts/check-bookshelf-categories-search.mjs",
```

Modify `AGENTS.md` by adding this command after `npm run test:reading-history` in the frontend packaging sequence:

```bash
npm run test:bookshelf-categories
```

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:bookshelf-categories
```

Expected: FAIL with `ENOENT` for `src/utils/bookshelfGrouping.ts`.

- [ ] **Step 3: Add frontend helper implementation**

Create `modules/web/src/utils/bookshelfGrouping.ts` with:

```ts
import type { Book } from '@/book'

export type BookshelfAuthorGroup = {
  author: string
  count: number
  books: Book[]
}

export type BookshelfCategoryGroup = {
  kind: string
  count: number
  authorGroups: BookshelfAuthorGroup[]
}

const FALLBACK_KIND = '未分類'
const FALLBACK_AUTHOR = '未知作者'
const SEARCH_PUNCTUATION = /[\s《》<>「」『』[\]【】()（）:：,，.。\-－_]/g
const SIMPLIFIED_TRADITIONAL_GROUPS: Array<readonly string[]> = [
  ['愛', '爱'],
  ['潛', '潜'],
  ['烏', '乌'],
  ['賊', '贼'],
  ['詭', '诡'],
  ['龍', '龙'],
  ['義', '义'],
  ['變', '变'],
  ['貓', '猫'],
  ['陳', '陈'],
  ['詞', '词'],
  ['懶', '懒'],
  ['調', '调'],
  ['書', '书'],
  ['職', '职'],
  ['蕭', '萧'],
  ['風', '风'],
  ['雲', '云'],
  ['聽', '听'],
  ['濤', '涛'],
  ['無', '无'],
  ['驚', '惊'],
  ['樂', '乐'],
]

const variantMap = new Map<string, string>(
  SIMPLIFIED_TRADITIONAL_GROUPS.flatMap(group =>
    group.map(char => [char, group[0]] as const),
  ),
)

const normalizeVariants = (value: string) =>
  Array.from(value)
    .map(char => variantMap.get(char) ?? char)
    .join('')

export const normalizeBookshelfSearch = (value?: string): string => {
  return normalizeVariants((value ?? '').trim().toLowerCase())
    .replace(SEARCH_PUNCTUATION, '')
}

export const bookMatchesLocalSearch = (book: Book, query: string): boolean => {
  const normalizedQuery = normalizeBookshelfSearch(query)
  if (normalizedQuery === '') return true
  return [
    book.name,
    book.author,
    book.kind,
    book.originName,
    book.bookUrl,
  ].some(value => normalizeBookshelfSearch(value).includes(normalizedQuery))
}

export const filterBookshelfBooks = (
  books: Book[],
  query: string,
): Book[] => books.filter(book => bookMatchesLocalSearch(book, query))

export const groupBookshelfBooks = (
  books: Book[],
): BookshelfCategoryGroup[] => {
  const categoryMap = new Map<string, Map<string, Book[]>>()

  for (const book of books) {
    const kind = book.kind || FALLBACK_KIND
    const author = book.author || FALLBACK_AUTHOR
    if (!categoryMap.has(kind)) categoryMap.set(kind, new Map())
    const authorMap = categoryMap.get(kind)!
    if (!authorMap.has(author)) authorMap.set(author, [])
    authorMap.get(author)!.push(book)
  }

  return Array.from(categoryMap.entries()).map(([kind, authorMap]) => {
    const authorGroups = Array.from(authorMap.entries()).map(
      ([author, authorBooks]) => ({
        author,
        count: authorBooks.length,
        books: authorBooks,
      }),
    )
    return {
      kind,
      count: authorGroups.reduce((sum, group) => sum + group.count, 0),
      authorGroups,
    }
  })
}
```

- [ ] **Step 4: Run frontend helper check and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:bookshelf-categories
```

Expected: PASS with no output.

- [ ] **Step 5: Run TypeScript check for helper**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run type-check
```

Expected: PASS.

- [ ] **Step 6: Commit frontend helper task**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add modules/web/src/utils/bookshelfGrouping.ts modules/web/scripts/check-bookshelf-categories-search.mjs modules/web/package.json AGENTS.md
git commit -m "feat: add bookshelf grouping search helpers"
```

Expected: commit includes helper, smoke check, package script, and AGENTS packaging update.

---

### Task 4: Render Grouped Local Bookshelf UI

**Files:**
- Modify: `modules/web/src/components/BookItems.vue`
- Modify: `modules/web/src/views/BookShelf.vue`
- Modify: `modules/web/scripts/check-bookshelf-categories-search.mjs`

**Interfaces:**
- Consumes:
  - `filterBookshelfBooks(books: Book[], query: string): Book[]`
  - `groupBookshelfBooks(books: Book[]): BookshelfCategoryGroup[]`
  - `normalizeBookshelfSearch(value?: string): string`
- Produces:
  - `BookItems` prop `embedded?: boolean`
  - `BookShelf.vue` computed `localBooks`, `groupedLocalBooks`, `localSearchActive`, and `localResultCount`
  - Online search results remain rendered with `<book-items :isSearch="true">`.

- [ ] **Step 1: Extend smoke check for UI wiring**

Append these assertions to `modules/web/scripts/check-bookshelf-categories-search.mjs` before the final `if (process.exitCode)` block:

```js
const bookShelf = read('src/views/BookShelf.vue')
const bookItems = read('src/components/BookItems.vue')

assertContains(
  bookShelf,
  /import \{[\s\S]*?filterBookshelfBooks[\s\S]*?groupBookshelfBooks[\s\S]*?normalizeBookshelfSearch[\s\S]*?\} from '@\/utils\/bookshelfGrouping'/,
  'BookShelf.vue must import bookshelf grouping and local search helpers.',
)

assertContains(
  bookShelf,
  /const onlineBooks = shallowRef<SeachBook\[\]>\(\[\]\)/,
  'BookShelf.vue must keep online search results separate from local shelf books.',
)

assertContains(
  bookShelf,
  /const isOnlineSearching = ref\(false\)/,
  'BookShelf.vue must distinguish online search mode from local search filtering.',
)

assertContains(
  bookShelf,
  /const localBooks = computed\(\(\) => filterBookshelfBooks\(shelf\.value, searchWord\.value\)\)/,
  'BookShelf.vue must filter local books with filterBookshelfBooks.',
)

assertContains(
  bookShelf,
  /const groupedLocalBooks = computed\(\(\) => groupBookshelfBooks\(localBooks\.value\)\)/,
  'BookShelf.vue must group filtered local books by category and author.',
)

assertContains(
  bookShelf,
  /v-for="category in groupedLocalBooks"[\s\S]*?category\.kind[\s\S]*?v-for="authorGroup in category\.authorGroups"[\s\S]*?authorGroup\.author/,
  'BookShelf.vue must render category sections and author groups.',
)

assertContains(
  bookShelf,
  /:books="authorGroup\.books"[\s\S]*?:embedded="true"/,
  'BookShelf.vue must render grouped books through embedded BookItems.',
)

assertContains(
  bookShelf,
  /v-if="isOnlineSearching"[\s\S]*?:books="onlineBooks"[\s\S]*?:isSearch="true"/,
  'BookShelf.vue must preserve online search result rendering.',
)

assertContains(
  bookItems,
  /embedded\?: boolean/,
  'BookItems.vue must accept an embedded prop.',
)

assertContains(
  bookItems,
  /:class="\{ 'books-wrapper': true, embedded \}"/,
  'BookItems.vue must expose embedded class state.',
)

assertContains(
  bookItems,
  /\.books-wrapper\.embedded\s*\{[\s\S]*?height:\s*auto;[\s\S]*?overflow:\s*visible;/,
  'Embedded BookItems must not create nested scroll containers.',
)
```

- [ ] **Step 2: Run UI smoke check and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:bookshelf-categories
```

Expected: FAIL with messages about missing `BookShelf.vue` imports, `onlineBooks`, `groupedLocalBooks`, and `BookItems` embedded prop.

- [ ] **Step 3: Add `embedded` support to `BookItems.vue`**

In `modules/web/src/components/BookItems.vue`, replace:

```vue
  <div class="books-wrapper">
```

with:

```vue
  <div :class="{ 'books-wrapper': true, embedded }">
```

Replace the props block:

```ts
const props = defineProps<{
  books: Array<Book | SeachBook>
  isSearch: boolean
}>()
```

with:

```ts
const props = withDefaults(
  defineProps<{
    books: Array<Book | SeachBook>
    isSearch: boolean
    embedded?: boolean
  }>(),
  {
    embedded: false,
  },
)
```

Add this CSS block after `.books-wrapper { ... }` and before `.books-wrapper::-webkit-scrollbar`:

```scss
.books-wrapper.embedded {
  height: auto;
  overflow: visible;

  .wrapper {
    justify-content: flex-start;
  }
}
```

- [ ] **Step 4: Replace local bookshelf state in `BookShelf.vue`**

In `modules/web/src/views/BookShelf.vue`, add this import after the chapter-link import:

```ts
import {
  filterBookshelfBooks,
  groupBookshelfBooks,
  normalizeBookshelfSearch,
} from '@/utils/bookshelfGrouping'
```

Replace this block:

```ts
// 书架书籍和在线书籍搜索
const books = shallowRef<Book[] | SeachBook[]>([])
const shelf = computed(() => store.shelf)
const searchWord = ref('')
const isSearching = ref(false)
watchEffect(() => {
  if (isSearching.value && searchWord.value != '') return
  isSearching.value = false
  books.value = []
  if (searchWord.value == '') {
    books.value = shelf.value
    return
  }
  books.value = shelf.value.filter(book => {
    return (
      book.name.includes(searchWord.value) ||
      book.author.includes(searchWord.value)
    )
  })
})
```

with:

```ts
// 书架书籍和在线书籍搜索
const onlineBooks = shallowRef<SeachBook[]>([])
const shelf = computed(() => store.shelf)
const searchWord = ref('')
const isOnlineSearching = ref(false)
const localBooks = computed(() => filterBookshelfBooks(shelf.value, searchWord.value))
const groupedLocalBooks = computed(() => groupBookshelfBooks(localBooks.value))
const localSearchActive = computed(
  () => normalizeBookshelfSearch(searchWord.value) !== '',
)
const localResultCount = computed(() => localBooks.value.length)

watch(searchWord, () => {
  isOnlineSearching.value = false
  onlineBooks.value = []
})
```

In `searchBook()`, replace:

```ts
  books.value = []
```

with:

```ts
  onlineBooks.value = []
```

Replace:

```ts
  isSearching.value = true
```

with:

```ts
  isOnlineSearching.value = true
```

Replace:

```ts
        books.value = store.searchBooks
```

with:

```ts
        onlineBooks.value = store.searchBooks
```

Replace:

```ts
      if (books.value.length == 0) {
```

with:

```ts
      if (onlineBooks.value.length == 0) {
```

- [ ] **Step 5: Replace bookshelf template in `BookShelf.vue`**

Replace this template block:

```vue
    <div class="shelf-wrapper" ref="shelfWrapper">
      <book-items
        :books="books"
        @bookClick="handleBookClick"
        :isSearch="isSearching"
      ></book-items>
    </div>
```

with:

```vue
    <div class="shelf-wrapper" ref="shelfWrapper">
      <book-items
        v-if="isOnlineSearching"
        :books="onlineBooks"
        @bookClick="handleBookClick"
        :isSearch="true"
      ></book-items>
      <div v-else class="grouped-shelf">
        <div class="shelf-summary">
          <span v-if="localSearchActive">本地搜索：{{ localResultCount }} 本</span>
          <span v-else>本地书架：{{ shelf.length }} 本</span>
        </div>
        <div
          v-if="groupedLocalBooks.length === 0"
          class="shelf-empty"
        >
          没有符合的本地书籍
        </div>
        <section
          v-for="category in groupedLocalBooks"
          :key="category.kind"
          class="category-section"
        >
          <div class="category-header">
            <h2>{{ category.kind }}</h2>
            <span>{{ category.count }} 本</span>
          </div>
          <section
            v-for="authorGroup in category.authorGroups"
            :key="`${category.kind}:${authorGroup.author}`"
            class="author-section"
          >
            <div class="author-header">
              <h3>{{ authorGroup.author }}</h3>
              <span>{{ authorGroup.count }} 本</span>
            </div>
            <book-items
              :books="authorGroup.books"
              @bookClick="handleBookClick"
              :isSearch="false"
              :embedded="true"
            ></book-items>
          </section>
        </section>
      </div>
    </div>
```

Change the search placeholder from:

```vue
placeholder="搜索书籍，在线书籍自动加入书架"
```

to:

```vue
placeholder="搜索本地书架，按 Enter 在线搜索"
```

- [ ] **Step 6: Add grouped shelf styles**

In `modules/web/src/views/BookShelf.vue`, add this SCSS inside `.shelf-wrapper { ... }` after its existing layout declarations:

```scss
    .grouped-shelf {
      height: 100%;
      min-height: 0;
      overflow: auto;
      -webkit-overflow-scrolling: touch;
    }

    .shelf-summary {
      color: #8c8c8c;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 16px;
    }

    .shelf-empty {
      color: #969ba3;
      font-size: 14px;
      padding: 24px 0;
    }

    .category-section {
      margin-bottom: 28px;
    }

    .category-header,
    .author-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
    }

    .category-header {
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      margin-bottom: 14px;
      padding-bottom: 8px;

      h2 {
        color: #33373d;
        font-size: 20px;
        font-weight: 700;
        margin: 0;
      }

      span {
        color: #8c8c8c;
        font-size: 12px;
      }
    }

    .author-section {
      margin-bottom: 18px;
    }

    .author-header {
      margin: 0 0 8px;

      h3 {
        color: #555b63;
        font-size: 14px;
        font-weight: 700;
        margin: 0;
      }

      span {
        color: #a0a0a0;
        font-size: 11px;
      }
    }
```

Add this inside the existing mobile `.shelf-wrapper { ... }` block:

```scss
      .grouped-shelf {
        padding: 0 0 20px;
      }

      .shelf-summary,
      .shelf-empty,
      .category-header,
      .author-header {
        padding-left: 20px;
        padding-right: 20px;
      }

      .category-header {
        margin-top: 14px;

        h2 {
          font-size: 17px;
        }
      }
```

Add this inside the existing `.night { .navigation-wrapper { ... } }` block or after it with a scoped `.night .shelf-wrapper` block:

```scss
.night {
  .shelf-wrapper {
    .category-header {
      border-bottom-color: rgba(255, 255, 255, 0.12);

      h2 {
        color: #d0d0d0;
      }
    }

    .author-header {
      h3 {
        color: #c0c0c0;
      }
    }
  }
}
```

- [ ] **Step 7: Run UI smoke check and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:bookshelf-categories
```

Expected: PASS with no output.

- [ ] **Step 8: Run frontend type-check and existing layout checks**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
```

Expected: all three commands PASS.

- [ ] **Step 9: Commit grouped UI task**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add modules/web/src/components/BookItems.vue modules/web/src/views/BookShelf.vue modules/web/scripts/check-bookshelf-categories-search.mjs
git commit -m "feat: group bookshelf by category and author"
```

Expected: commit includes UI wiring and updated smoke assertions.

---

### Task 5: Rebuild Embedded Frontend and Verify Server JAR

**Files:**
- Generated: `modules/web/dist/`
- Modify generated assets: `legado-server/src/main/resources/web/`
- Build artifact: `legado-server/build/libs/legado-server-all.jar`

**Interfaces:**
- Consumes: all previous server and frontend commits.
- Produces: verified embedded frontend resources and a fresh `legado-server-all.jar`.

- [ ] **Step 1: Run full frontend check sequence**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:reading-history
npm run test:bookshelf-categories
npm run test:history-router
npm run test:cover-safety
npm run build-only
```

Expected: every command PASS; `build-only` refreshes `modules/web/dist/`.

- [ ] **Step 2: Copy frontend dist into embedded server resources**

Run:

```bash
cd /run/media/allen/500G/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/
```

Expected: `legado-server/src/main/resources/web/index.html` and hashed asset files match the new Vite build.

- [ ] **Step 3: Run server test and JAR build from standalone server project**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test shadowJar
```

Expected: all Gradle tests PASS and `build/libs/legado-server-all.jar` is produced.

- [ ] **Step 4: Verify embedded asset and checksum evidence**

Run:

```bash
cd /run/media/allen/500G/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
jar tf legado-server/build/libs/legado-server-all.jar | rg 'web/(index\.html|assets/(BookShelf|BookChapter|index)-)'
diff -qr modules/web/dist legado-server/src/main/resources/web
```

Expected:

- `sha256sum` prints one checksum for `legado-server-all.jar`.
- `jar tf` lists `web/index.html` and current hashed frontend assets.
- `diff -qr` prints no differences.

- [ ] **Step 5: Commit embedded frontend resources**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/main/resources/web
git commit -m "build: refresh embedded bookshelf assets"
```

Expected: commit includes only refreshed embedded frontend files.

- [ ] **Step 6: Optional deploy to `allen@800g4` when requested**

Run only if deployment is requested:

```bash
cd /run/media/allen/500G/projects/legado
LOCAL_SUM=$(sha256sum legado-server/build/libs/legado-server-all.jar | awk '{print $1}')
scp legado-server/build/libs/legado-server-all.jar allen@800g4:/tmp/legado-server-all.jar.new
REMOTE_SUM=$(ssh allen@800g4 'sha256sum /tmp/legado-server-all.jar.new' | awk '{print $1}')
test "$LOCAL_SUM" = "$REMOTE_SUM"
ssh allen@800g4 'sudo install -o legado -g legado -m 0644 /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar && sudo systemctl restart legado-server && sha256sum /opt/legado-server/legado-server-all.jar && systemctl status legado-server --no-pager -l'
ssh allen@800g4 'curl -fsS --max-time 10 http://127.0.0.1:8081/getBookshelf | jq "{total:(.data|length), author_nonblank:(.data|map(select((.author // \"\") != \"\"))|length), kind_nonblank:(.data|map(select((.kind // \"\") != \"\"))|length), sample:(.data[0:5]|map({name,author,kind,bookUrl}))}"'
```

Expected:

- Local and remote `/tmp` checksums match before install.
- `legado-server.service` is `active (running)`.
- `/getBookshelf` reports nonzero `author_nonblank` and nonzero `kind_nonblank` after the service rescans.

If Nginx access-log verification is required, run:

```bash
ssh allen@800g4 'docker exec nginx sh -c "tail -n 220 /data/logs/proxy-host-8_access.log | grep -E \"BookShelf|index-|getBookshelf\" | tail -n 80"'
```

Expected: access logs show the newly hashed `BookShelf` and `index` chunks after browser reload.
