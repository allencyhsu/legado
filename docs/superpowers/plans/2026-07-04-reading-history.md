# Reading History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build server-backed reading history in the Legado web server and web frontend, with open, single-delete, and clear-all behavior.

**Architecture:** Reuse the existing SQLite `read_progress` table as the history source, exposing focused Ktor endpoints for listing and deleting progress rows. The Vue bookshelf page loads server history into a compact left-column list, falls back to the existing browser `readingRecent` record when server history is empty or unavailable, and keeps generated frontend assets embedded under `legado-server/src/main/resources/web/`.

**Tech Stack:** Kotlin 2.0, Ktor 3, SQLite with Exposed 0.53, Kotlin test, Vue 3 Composition API, Pinia, Element Plus, TypeScript, Vite, Node smoke-test scripts, Gradle shadowJar.

## Global Constraints

- Every assistant response must include the name "Allen".
- A history item is one book's saved reading progress row; do not add paragraph-level bookmarks or annotations inside chapter content.
- Deleting history removes `read_progress` rows only; it must not delete books or cached chapters.
- Reuse the existing `read_progress` table; no database schema migration is needed.
- Frontend changes under `modules/web/` require rebuilding `modules/web/dist/` and copying it into `legado-server/src/main/resources/web/`.
- Run `shadowJar` only from `legado-server/`; do not run `./gradlew shadowJar` from the repository root.
- This checkout is at `/run/media/allen/500G/projects/legado`; use that path in local commands.
- If a development tool must be installed, install it outside the repo under `/run/media/allen/500G/projects/`.

---

## File Structure

- Modify: `legado-server/src/main/kotlin/io/legado/server/data/BookRepository.kt`
  - Add `getReadingHistory(): List<Book>`.
  - Add `deleteReadProgress(bookUrl: String): Int`.
  - Add `clearReadProgress(): Int`.

- Modify: `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`
  - Add thin service methods for reading history list, single delete, and clear all.

- Modify: `legado-server/src/main/kotlin/io/legado/server/routes/ProgressRoutes.kt`
  - Add `GET /getReadingHistory`.
  - Add `POST /deleteReadingHistory`.
  - Add `POST /clearReadingHistory`.
  - Add `ReadingHistoryDeleteRequest`.

- Create: `legado-server/src/test/kotlin/io/legado/server/service/BookServiceReadingHistoryTest.kt`
  - Verify history order, single progress delete, and clear-all behavior.

- Create: `legado-server/src/test/kotlin/io/legado/server/ReadingHistoryRoutingTest.kt`
  - Verify route JSON and route-driven mutations.

- Modify: `modules/web/src/book.d.ts`
  - Add `ReadingHistoryDeleteRequest = { bookUrl: string }`.

- Modify: `modules/web/src/api/api.ts`
  - Add `getReadingHistory`, `deleteReadingHistory`, and `clearReadingHistory`.

- Modify: `modules/web/src/views/BookShelf.vue`
  - Replace the single recent tag with a reading history list.
  - Keep the existing `readingRecent` fallback.
  - Add single delete and clear-all controls.

- Create: `modules/web/scripts/check-reading-history.mjs`
  - Guard API wiring and bookshelf history controls.

- Modify: `modules/web/package.json`
  - Add `test:reading-history`.

- Modify: `AGENTS.md`
  - Add `npm run test:reading-history` to the required frontend packaging sequence.

- Generated after build: `modules/web/dist/`
  - Built by Vite.

- Modify generated assets: `legado-server/src/main/resources/web/`
  - Refresh from `modules/web/dist/` before building the server JAR.

---

### Task 1: Add Repository and Service Reading History

**Files:**
- Create: `legado-server/src/test/kotlin/io/legado/server/service/BookServiceReadingHistoryTest.kt`
- Modify: `legado-server/src/main/kotlin/io/legado/server/data/BookRepository.kt`
- Modify: `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`

**Interfaces:**
- Consumes: existing `Database.ReadProgress`, `BookRepository.saveReadProgress(progress: ReadProgressData)`, and `BookRepository.getReadProgress(bookUrl: String): ReadProgressData?`.
- Produces:
  - `BookRepository.getReadingHistory(): List<Book>`
  - `BookRepository.deleteReadProgress(bookUrl: String): Int`
  - `BookRepository.clearReadProgress(): Int`
  - `BookService.getReadingHistory(): List<Book>`
  - `BookService.deleteReadingHistory(bookUrl: String): Int`
  - `BookService.clearReadingHistory(): Int`

- [ ] **Step 1: Write the failing repository and service tests**

Create `legado-server/src/test/kotlin/io/legado/server/service/BookServiceReadingHistoryTest.kt` with this content:

```kotlin
package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookServiceReadingHistoryTest {
    @Test
    fun `reading history returns books ordered by newest progress first`() {
        initTempDatabase()
        val oldBook = Book(bookUrl = "/books/old.txt", name = "Old Book")
        val newBook = Book(bookUrl = "/books/new.txt", name = "New Book")
        val unreadBook = Book(bookUrl = "/books/unread.txt", name = "Unread Book")
        BookRepository.upsertBook(oldBook)
        BookRepository.upsertBook(newBook)
        BookRepository.upsertBook(unreadBook)
        BookRepository.saveReadProgress(progress(oldBook, chapterTime = 100))
        BookRepository.saveReadProgress(progress(newBook, chapterTime = 200))

        val history = BookService("/books").getReadingHistory()

        assertEquals(listOf("New Book", "Old Book"), history.map { it.name })
        assertEquals(listOf(200L, 100L), history.map { it.durChapterTime })
        assertEquals(listOf(2, 1), history.map { it.durChapterIndex })
    }

    @Test
    fun `deleting one reading history item removes progress but keeps the book`() {
        initTempDatabase()
        val book = Book(bookUrl = "/books/history.txt", name = "History Book")
        BookRepository.upsertBook(book)
        BookRepository.saveReadProgress(progress(book, chapterTime = 300))

        val deletedCount = BookService("/books").deleteReadingHistory(book.bookUrl)

        assertEquals(1, deletedCount)
        assertNotNull(BookRepository.getBook(book.bookUrl))
        assertNull(BookRepository.getReadProgress(book.bookUrl))
    }

    @Test
    fun `clearing reading history removes all progress but keeps books`() {
        initTempDatabase()
        val firstBook = Book(bookUrl = "/books/first.txt", name = "First Book")
        val secondBook = Book(bookUrl = "/books/second.txt", name = "Second Book")
        BookRepository.upsertBook(firstBook)
        BookRepository.upsertBook(secondBook)
        BookRepository.saveReadProgress(progress(firstBook, chapterTime = 400))
        BookRepository.saveReadProgress(progress(secondBook, chapterTime = 500))

        val deletedCount = BookService("/books").clearReadingHistory()

        assertEquals(2, deletedCount)
        assertNotNull(BookRepository.getBook(firstBook.bookUrl))
        assertNotNull(BookRepository.getBook(secondBook.bookUrl))
        assertNull(BookRepository.getReadProgress(firstBook.bookUrl))
        assertNull(BookRepository.getReadProgress(secondBook.bookUrl))
    }

    private fun initTempDatabase() {
        val dbPath = Files.createTempDirectory("legado-reading-history-db").resolve("legado.db")
        Database.init(dbPath.toString())
    }

    private fun progress(book: Book, chapterTime: Long) = ReadProgressData(
        bookUrl = book.bookUrl,
        durChapterIndex = if (chapterTime >= 200) 2 else 1,
        durChapterPos = 12,
        durChapterTime = chapterTime,
        durChapterTitle = "Chapter $chapterTime"
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.BookServiceReadingHistoryTest
```

Expected: FAIL during Kotlin compilation with unresolved references for `getReadingHistory`, `deleteReadingHistory`, and `clearReadingHistory`.

- [ ] **Step 3: Add repository helpers**

In `legado-server/src/main/kotlin/io/legado/server/data/BookRepository.kt`, add these methods after `saveReadProgress(progress: ReadProgressData)`:

```kotlin
    fun getReadingHistory(): List<Book> = transaction {
        Database.Books.innerJoin(Database.ReadProgress)
            .selectAll()
            .orderBy(Database.ReadProgress.durChapterTime, SortOrder.DESC)
            .map { it.toBook() }
    }

    fun deleteReadProgress(bookUrl: String): Int = transaction {
        Database.ReadProgress.deleteWhere { Database.ReadProgress.bookUrl eq bookUrl }
    }

    fun clearReadProgress(): Int = transaction {
        Database.ReadProgress.deleteAll()
    }
```

- [ ] **Step 4: Add service methods**

In `legado-server/src/main/kotlin/io/legado/server/service/BookService.kt`, add these methods after `saveProgress(progress: BookProgressRequest)`:

```kotlin
    /**
     * Get server-backed reading history.
     */
    fun getReadingHistory(): List<Book> {
        return BookRepository.getReadingHistory()
    }

    /**
     * Delete one reading history item without deleting the book.
     */
    fun deleteReadingHistory(bookUrl: String): Int {
        return BookRepository.deleteReadProgress(bookUrl)
    }

    /**
     * Clear all reading history without deleting books.
     */
    fun clearReadingHistory(): Int {
        return BookRepository.clearReadProgress()
    }
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.BookServiceReadingHistoryTest
```

Expected: PASS for all three tests in `BookServiceReadingHistoryTest`.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/test/kotlin/io/legado/server/service/BookServiceReadingHistoryTest.kt legado-server/src/main/kotlin/io/legado/server/data/BookRepository.kt legado-server/src/main/kotlin/io/legado/server/service/BookService.kt
git commit -m "feat: add reading history data access"
```

---

### Task 2: Add Reading History HTTP Routes

**Files:**
- Create: `legado-server/src/test/kotlin/io/legado/server/ReadingHistoryRoutingTest.kt`
- Modify: `legado-server/src/main/kotlin/io/legado/server/routes/ProgressRoutes.kt`

**Interfaces:**
- Consumes from Task 1:
  - `BookService.getReadingHistory(): List<Book>`
  - `BookService.deleteReadingHistory(bookUrl: String): Int`
  - `BookService.clearReadingHistory(): Int`
- Produces:
  - `GET /getReadingHistory` returning `ReturnData<List<Book>>`
  - `POST /deleteReadingHistory` accepting `ReadingHistoryDeleteRequest`
  - `POST /clearReadingHistory` returning `ReturnData<Int>`
  - `data class ReadingHistoryDeleteRequest(val bookUrl: String)`

- [ ] **Step 1: Write the failing route test**

Create `legado-server/src/test/kotlin/io/legado/server/ReadingHistoryRoutingTest.kt` with this content:

```kotlin
package io.legado.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import io.legado.server.service.BookService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingHistoryRoutingTest {
    @Test
    fun `reading history routes list delete and clear progress`() = withReadingHistoryServer {
        val listResponse = client.get("/getReadingHistory")
        val listBody = listResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listBody.contains("\"isSuccess\": true"))
        assertTrue(listBody.indexOf("New Route Book") < listBody.indexOf("Old Route Book"))

        val deleteResponse = client.post("/deleteReadingHistory") {
            contentType(ContentType.Application.Json)
            setBody("""{"bookUrl":"/books/old-route.txt"}""")
        }
        val deleteBody = deleteResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        assertTrue(deleteBody.contains("\"isSuccess\": true"))
        assertNotNull(BookRepository.getBook("/books/old-route.txt"))
        assertNull(BookRepository.getReadProgress("/books/old-route.txt"))
        assertNotNull(BookRepository.getReadProgress("/books/new-route.txt"))

        val clearResponse = client.post("/clearReadingHistory")
        val clearBody = clearResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, clearResponse.status)
        assertTrue(clearBody.contains("\"isSuccess\": true"))
        assertTrue(clearBody.contains("\"data\": 1"))
        assertNotNull(BookRepository.getBook("/books/new-route.txt"))
        assertNull(BookRepository.getReadProgress("/books/new-route.txt"))
    }

    @Test
    fun `delete reading history rejects missing bookUrl`() = withReadingHistoryServer {
        val response = client.post("/deleteReadingHistory") {
            contentType(ContentType.Application.Json)
            setBody("""{"bookUrl":""}""")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"isSuccess\": false"))
        assertTrue(body.contains("Missing bookUrl"))
    }

    private fun withReadingHistoryServer(testBlock: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dbPath = Files.createTempDirectory("legado-reading-history-routing-db").resolve("legado.db")
        val booksDir = Files.createTempDirectory("legado-reading-history-routing-books")
        val upstreamClient = HttpClient(CIO)
        try {
            Database.init(dbPath.toString())
            seedHistory()
            application {
                configureLegadoServer(
                    BookService(booksDir.toString()),
                    upstreamClient,
                    "http://127.0.0.1:65535"
                )
            }
            testBlock()
        } finally {
            upstreamClient.close()
            booksDir.toFile().deleteRecursively()
        }
    }

    private fun seedHistory() {
        val oldBook = Book(bookUrl = "/books/old-route.txt", name = "Old Route Book")
        val newBook = Book(bookUrl = "/books/new-route.txt", name = "New Route Book")
        BookRepository.upsertBook(oldBook)
        BookRepository.upsertBook(newBook)
        BookRepository.saveReadProgress(progress(oldBook, chapterTime = 1000))
        BookRepository.saveReadProgress(progress(newBook, chapterTime = 2000))
    }

    private fun progress(book: Book, chapterTime: Long) = ReadProgressData(
        bookUrl = book.bookUrl,
        durChapterIndex = 3,
        durChapterPos = 15,
        durChapterTime = chapterTime,
        durChapterTitle = "Route Chapter $chapterTime"
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.ReadingHistoryRoutingTest
```

Expected: FAIL because `/getReadingHistory`, `/deleteReadingHistory`, and `/clearReadingHistory` are not registered routes.

- [ ] **Step 3: Add the routes and request type**

In `legado-server/src/main/kotlin/io/legado/server/routes/ProgressRoutes.kt`, add these route blocks inside `fun Route.progressRoutes(bookService: BookService)` after the existing `/saveBookProgress` route:

```kotlin
    /**
     * GET /getReadingHistory
     * Returns books with saved reading progress, newest first.
     */
    get("/getReadingHistory") {
        try {
            call.respond(ReturnData.success(bookService.getReadingHistory()))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to get reading history"))
        }
    }

    /**
     * POST /deleteReadingHistory
     * Deletes one reading history item without deleting the book.
     * Request body: { bookUrl }
     */
    post("/deleteReadingHistory") {
        try {
            val request = call.receive<ReadingHistoryDeleteRequest>()
            if (request.bookUrl.isBlank()) {
                call.respond(ReturnData.error("Missing bookUrl"))
                return@post
            }
            bookService.deleteReadingHistory(request.bookUrl)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to delete reading history"))
        }
    }

    /**
     * POST /clearReadingHistory
     * Deletes all reading history items without deleting books.
     */
    post("/clearReadingHistory") {
        try {
            val deletedCount = bookService.clearReadingHistory()
            call.respond(ReturnData.success(deletedCount))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to clear reading history"))
        }
    }
```

Add this data class below `BookProgressRequest` in the same file:

```kotlin
data class ReadingHistoryDeleteRequest(
    val bookUrl: String
)
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.ReadingHistoryRoutingTest
```

Expected: PASS for both route tests.

- [ ] **Step 5: Run the Task 1 server test again**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test --tests io.legado.server.service.BookServiceReadingHistoryTest
```

Expected: PASS for all three service tests.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/test/kotlin/io/legado/server/ReadingHistoryRoutingTest.kt legado-server/src/main/kotlin/io/legado/server/routes/ProgressRoutes.kt
git commit -m "feat: expose reading history routes"
```

---

### Task 3: Add Frontend API Types and Reading History Guard

**Files:**
- Create: `modules/web/scripts/check-reading-history.mjs`
- Modify: `modules/web/package.json`
- Modify: `AGENTS.md`
- Modify: `modules/web/src/book.d.ts`
- Modify: `modules/web/src/api/api.ts`

**Interfaces:**
- Consumes from Task 2:
  - `GET /getReadingHistory`
  - `POST /deleteReadingHistory`
  - `POST /clearReadingHistory`
- Produces:
  - `ReadingHistoryDeleteRequest`
  - `API.getReadingHistory()`
  - `API.deleteReadingHistory(request: ReadingHistoryDeleteRequest)`
  - `API.clearReadingHistory()`
  - `npm run test:reading-history`

- [ ] **Step 1: Write the failing frontend guard**

Create `modules/web/scripts/check-reading-history.mjs` with this content:

```js
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const api = read('src/api/api.ts')
const bookTypes = read('src/book.d.ts')
const bookShelf = read('src/views/BookShelf.vue')

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

assertContains(
  bookTypes,
  /export type ReadingHistoryDeleteRequest = \{\s*bookUrl: string\s*\}/,
  'book.d.ts must define ReadingHistoryDeleteRequest with bookUrl.',
)

assertContains(
  api,
  /const getReadingHistory = \(\) =>\s*ajax\.get<LeagdoApiResponse<Book\[\]>>\(['"]getReadingHistory['"]\)/,
  'api.ts must expose getReadingHistory returning Book[].',
)

assertContains(
  api,
  /const deleteReadingHistory = \(request: ReadingHistoryDeleteRequest\) =>\s*ajax\.post<LeagdoApiResponse<string>>\(['"]deleteReadingHistory['"], request\)/,
  'api.ts must expose deleteReadingHistory with ReadingHistoryDeleteRequest.',
)

assertContains(
  api,
  /const clearReadingHistory = \(\) =>\s*ajax\.post<LeagdoApiResponse<number>>\(['"]clearReadingHistory['"]\)/,
  'api.ts must expose clearReadingHistory returning deleted count.',
)

assertContains(
  bookShelf,
  /const readingHistory = ref<Book\[\]>\(\[\]\)/,
  'BookShelf must keep server reading history in a typed ref.',
)

assertContains(
  bookShelf,
  /API\.getReadingHistory\(\)/,
  'BookShelf must load reading history from the server.',
)

assertContains(
  bookShelf,
  /API\.deleteReadingHistory\(\{\s*bookUrl: item\.bookUrl\s*\}\)/,
  'BookShelf must delete one reading history item by bookUrl.',
)

assertContains(
  bookShelf,
  /API\.clearReadingHistory\(\)/,
  'BookShelf must clear all reading history through the API.',
)

assertContains(
  bookShelf,
  /removeLocalStorageItem\(['"]readingRecent['"]\)/,
  'BookShelf must clear the local readingRecent fallback when deleting matching history.',
)

assertContains(
  bookShelf,
  /@click\.stop\.prevent=["']deleteReadingHistory\(item\)["']/,
  'BookShelf single-item delete control must not open the book while deleting.',
)

if (process.exitCode) process.exit(process.exitCode)
```

- [ ] **Step 2: Add the npm script**

In `modules/web/package.json`, add this script after `test:reading-state`:

```json
"test:reading-history": "node scripts/check-reading-history.mjs",
```

The script block must contain this sequence:

```json
"test:mobile-layout": "node scripts/check-mobile-bookshelf-layout.mjs",
"test:mobile-activation": "node scripts/check-mobile-book-activation.mjs",
"test:mobile-chapter-hotspots": "node scripts/check-mobile-chapter-hotspots.mjs",
"test:history-router": "node scripts/check-history-router.mjs",
"test:cover-safety": "node scripts/check-cover-request-safety.mjs",
"test:reading-state": "node scripts/check-reading-state-resilience.mjs",
"test:reading-history": "node scripts/check-reading-history.mjs",
```

- [ ] **Step 3: Add the new frontend check to AGENTS.md**

In `AGENTS.md`, add the new script after `npm run test:reading-state` in the frontend packaging sequence:

```bash
npm run test:reading-history
```

- [ ] **Step 4: Run the new guard and verify RED**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:reading-history
```

Expected: FAIL with messages for missing `ReadingHistoryDeleteRequest`, missing API functions, and missing `BookShelf` history wiring.

- [ ] **Step 5: Add the frontend delete request type**

In `modules/web/src/book.d.ts`, add this type after `BookProgress`:

```ts
export type ReadingHistoryDeleteRequest = {
  bookUrl: string
}
```

- [ ] **Step 6: Add API functions**

In `modules/web/src/api/api.ts`, add `ReadingHistoryDeleteRequest` to the type import:

```ts
import type {
  BaseBook,
  Book,
  BookChapter,
  BookProgress,
  ReadingHistoryDeleteRequest,
  SeachBook,
} from '@/book'
```

Add these functions after `const getBookShelf = () => ajax.get<LeagdoApiResponse<Book[]>>('getBookshelf')`:

```ts
const getReadingHistory = () =>
  ajax.get<LeagdoApiResponse<Book[]>>('getReadingHistory')

const deleteReadingHistory = (request: ReadingHistoryDeleteRequest) =>
  ajax.post<LeagdoApiResponse<string>>('deleteReadingHistory', request)

const clearReadingHistory = () =>
  ajax.post<LeagdoApiResponse<number>>('clearReadingHistory')
```

Add the functions to the default export object after `getBookShelf`:

```ts
  getReadingHistory,
  deleteReadingHistory,
  clearReadingHistory,
```

- [ ] **Step 7: Run the new guard and confirm it still fails only on BookShelf wiring**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:reading-history
```

Expected: FAIL with messages mentioning `BookShelf`, `API.getReadingHistory`, `API.deleteReadingHistory`, `API.clearReadingHistory`, local `readingRecent`, and `@click.stop.prevent`.

- [ ] **Step 8: Commit Task 3 partial frontend API and guard**

Do not commit yet. Keep these changes for Task 4 so the frontend guard is committed together with the UI implementation that makes it pass.

---

### Task 4: Build the Bookshelf Reading History UI

**Files:**
- Modify: `modules/web/src/views/BookShelf.vue`
- Modify from Task 3: `modules/web/scripts/check-reading-history.mjs`
- Modify from Task 3: `modules/web/package.json`
- Modify from Task 3: `AGENTS.md`
- Modify from Task 3: `modules/web/src/book.d.ts`
- Modify from Task 3: `modules/web/src/api/api.ts`

**Interfaces:**
- Consumes from Task 3:
  - `API.getReadingHistory()`
  - `API.deleteReadingHistory({ bookUrl: string })`
  - `API.clearReadingHistory()`
- Produces:
  - `readingHistory: Ref<Book[]>`
  - `readingHistoryItems: ComputedRef<ReadingHistoryItem[]>`
  - `loadReadingHistory(): Promise<void>`
  - `deleteReadingHistory(item: ReadingHistoryItem): Promise<void>`
  - `clearReadingHistory(): Promise<void>`

- [ ] **Step 1: Update imports**

In `modules/web/src/views/BookShelf.vue`, change the Element Plus icon import from:

```ts
import { Search as SearchIcon } from '@element-plus/icons-vue'
```

to:

```ts
import {
  CloseBold as CloseBoldIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
} from '@element-plus/icons-vue'
```

- [ ] **Step 2: Add the local history item type**

After the existing `type { webReadConfig } from '@/web'` import, add:

```ts
type ReadingHistoryItem = {
  name: string
  author: string
  bookUrl: string
  chapterIndex: number
  chapterPos: number
  chapterTitle: string
  isSeachBook?: boolean
}
```

- [ ] **Step 3: Add server history state and derived list**

After the existing `const readingRecent = ref<typeof store.readingBook>({ ... })` block, add:

```ts
const readingHistory = ref<Book[]>([])

const toHistoryItem = (book: Book): ReadingHistoryItem => ({
  name: book.name,
  author: book.author,
  bookUrl: book.bookUrl,
  chapterIndex: book.durChapterIndex ?? 0,
  chapterPos: book.durChapterPos ?? 0,
  chapterTitle:
    book.durChapterTitle || `第${(book.durChapterIndex ?? 0) + 1}章`,
  isSeachBook: false,
})

const toRecentHistoryItem = (): ReadingHistoryItem | undefined => {
  if (readingRecent.value.bookUrl === '') return undefined
  return {
    name: readingRecent.value.name,
    author: readingRecent.value.author,
    bookUrl: readingRecent.value.bookUrl,
    chapterIndex: readingRecent.value.chapterIndex,
    chapterPos: readingRecent.value.chapterPos,
    chapterTitle: '本机记录',
    isSeachBook: readingRecent.value.isSeachBook,
  }
}

const readingHistoryItems = computed<ReadingHistoryItem[]>(() => {
  if (readingHistory.value.length > 0) {
    return readingHistory.value.map(toHistoryItem)
  }
  const recentItem = toRecentHistoryItem()
  return recentItem === undefined ? [] : [recentItem]
})
```

- [ ] **Step 4: Replace the recent-reading template block**

Replace the current `<div class="recent-wrapper">...</div>` block inside `.bottom-wrapper` with this block:

```vue
        <div class="recent-wrapper">
          <div class="recent-title-row">
            <div class="recent-title">阅读历史</div>
            <el-button
              v-if="readingHistoryItems.length > 0"
              class="history-clear"
              link
              size="small"
              type="danger"
              :icon="DeleteIcon"
              @click="clearReadingHistory"
            >
              清空
            </el-button>
          </div>
          <div class="reading-history">
            <a
              v-for="item in readingHistoryItems"
              :key="item.bookUrl"
              class="history-item"
              :href="getChapterHref(item)"
              @click="handleHistoryClick($event, item)"
            >
              <span class="history-main">
                <span class="history-name">{{ item.name }}</span>
                <span class="history-chapter">{{ item.chapterTitle }}</span>
              </span>
              <el-button
                class="history-delete"
                text
                circle
                size="small"
                type="danger"
                :icon="CloseBoldIcon"
                :aria-label="`删除${item.name}的阅读历史`"
                @click.stop.prevent="deleteReadingHistory(item)"
              />
            </a>
            <el-tag
              v-if="readingHistoryItems.length === 0"
              type="warning"
              class="recent-book"
              size="large"
            >
              尚无阅读记录
            </el-tag>
          </div>
        </div>
```

- [ ] **Step 5: Add history loading and deletion methods**

Replace `handleRecentClick` with these functions:

```ts
const openHistoryItem = (
  item: ReadingHistoryItem,
  event?: MouseEvent,
) => {
  if (item.bookUrl === '') {
    event?.preventDefault()
    return
  }
  toDetail(
    item.bookUrl,
    item.name,
    item.author,
    item.chapterIndex,
    item.chapterPos,
    item.isSeachBook,
    true,
  )
}

const handleHistoryClick = (
  event: MouseEvent,
  item: ReadingHistoryItem,
) => {
  openHistoryItem(item, event)
}

const removeReadingRecentIfMatches = (bookUrl: string) => {
  if (readingRecent.value.bookUrl !== bookUrl) return
  readingRecent.value = {
    name: '尚无阅读记录',
    author: '',
    bookUrl: '',
    chapterIndex: 0,
    chapterPos: 0,
    isSeachBook: false,
  }
  removeLocalStorageItem('readingRecent')
}

const loadReadingHistory = async () => {
  try {
    const resp = await API.getReadingHistory()
    const { isSuccess, data, errorMsg } = resp.data
    if (isSuccess === true) {
      readingHistory.value = data
      return
    }
    ElMessage.error(errorMsg || '阅读历史加载失败')
  } catch {
    readingHistory.value = []
  }
}

const deleteReadingHistory = async (item: ReadingHistoryItem) => {
  try {
    const resp = await API.deleteReadingHistory({ bookUrl: item.bookUrl })
    const { isSuccess, errorMsg } = resp.data
    if (isSuccess !== true) {
      ElMessage.error(errorMsg || '阅读历史删除失败')
      return
    }
    readingHistory.value = readingHistory.value.filter(
      book => book.bookUrl !== item.bookUrl,
    )
    removeReadingRecentIfMatches(item.bookUrl)
  } catch {
    ElMessage.error('阅读历史删除失败')
  }
}

const clearReadingHistory = async () => {
  try {
    await ElMessageBox.confirm('确定清空全部阅读历史？', '清空阅读历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const resp = await API.clearReadingHistory()
    const { isSuccess, errorMsg } = resp.data
    if (isSuccess !== true) {
      ElMessage.error(errorMsg || '阅读历史清空失败')
      return
    }
    readingHistory.value = []
    readingRecent.value = {
      name: '尚无阅读记录',
      author: '',
      bookUrl: '',
      chapterIndex: 0,
      chapterPos: 0,
      isSeachBook: false,
    }
    removeLocalStorageItem('readingRecent')
  } catch {
    ElMessage.error('阅读历史清空失败')
  }
}
```

- [ ] **Step 6: Load history together with the shelf**

Change `loadShelf` from:

```ts
const loadShelf = async () => {
  await store.loadWebConfig()
  await store.saveBookProgress()
  //确保各种网络情况下同步请求先完成
  await store.loadBookShelf()
}
```

to:

```ts
const loadShelf = async () => {
  await store.loadWebConfig()
  await store.saveBookProgress()
  //确保各种网络情况下同步请求先完成
  await store.loadBookShelf()
  await loadReadingHistory()
}
```

- [ ] **Step 7: Update the recent-wrapper styles**

In the scoped style block, replace the nested `.recent-wrapper` block with:

```scss
    .recent-wrapper {
      margin-top: 36px;

      .recent-title-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
      }

      .recent-title {
        font-size: 14px;
        color: #b1b1b1;
        font-family: FZZCYSK;
      }

      .history-clear {
        min-width: 42px;
        padding: 0;
      }

      .reading-history {
        margin: 16px 0 0;
        display: flex;
        flex-direction: column;
        gap: 8px;

        .history-item {
          color: inherit;
          text-decoration: none;
          display: grid;
          grid-template-columns: minmax(0, 1fr) 28px;
          align-items: center;
          gap: 6px;
          min-height: 34px;
          padding: 2px 0;
        }

        .history-main {
          display: flex;
          min-width: 0;
          flex-direction: column;
          gap: 2px;
        }

        .history-name,
        .history-chapter {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .history-name {
          color: #33373d;
          font-size: 12px;
          font-weight: 600;
        }

        .history-chapter {
          color: #8c8c8c;
          font-size: 10px;
        }

        .history-delete {
          width: 28px;
          height: 28px;
        }

        .recent-book {
          width: fit-content;
          max-width: 100%;
          font-size: 10px;
          cursor: default;
        }
      }
    }
```

Inside the existing `.night { .navigation-wrapper { ... } }` block, add:

```scss
    .reading-history {
      .history-name {
        color: #d0d0d0;
      }

      .history-chapter {
        color: #a0a0a0;
      }
    }
```

- [ ] **Step 8: Run the reading history guard and verify GREEN**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:reading-history
```

Expected: PASS.

- [ ] **Step 9: Run TypeScript check**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run type-check
```

Expected: PASS with no TypeScript errors.

- [ ] **Step 10: Run existing focused frontend guards**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
```

Expected: all commands PASS.

- [ ] **Step 11: Commit Tasks 3 and 4 together**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add AGENTS.md modules/web/package.json modules/web/scripts/check-reading-history.mjs modules/web/src/book.d.ts modules/web/src/api/api.ts modules/web/src/views/BookShelf.vue
git commit -m "feat: add bookshelf reading history controls"
```

---

### Task 5: Rebuild Embedded Frontend and Produce Server JAR

**Files:**
- Generated: `modules/web/dist/`
- Modify generated assets: `legado-server/src/main/resources/web/`
- Generated: `legado-server/build/libs/legado-server-all.jar`

**Interfaces:**
- Consumes from Tasks 1-4:
  - Server history routes.
  - Frontend history API and bookshelf controls.
- Produces:
  - Updated embedded frontend resources under `legado-server/src/main/resources/web/`.
  - Verified server JAR at `legado-server/build/libs/legado-server-all.jar`.

- [ ] **Step 1: Run the full frontend verification sequence**

Run:

```bash
cd /run/media/allen/500G/projects/legado/modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:mobile-chapter-hotspots
npm run test:reading-state
npm run test:reading-history
npm run test:history-router
npm run test:cover-safety
npm run build-only
```

Expected: every npm command exits 0. `npm run build-only` prints a Vite build success and writes current hashed assets under `modules/web/dist/assets/`.

- [ ] **Step 2: Sync frontend build into server resources**

Run:

```bash
cd /run/media/allen/500G/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/
```

Expected: `legado-server/src/main/resources/web/index.html` and hashed assets match `modules/web/dist/`.

- [ ] **Step 3: Run full server tests and build the fat JAR**

Run:

```bash
cd /run/media/allen/500G/projects/legado/legado-server
./gradlew test shadowJar
```

Expected: Gradle prints `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify the JAR checksum and embedded frontend assets**

Run:

```bash
cd /run/media/allen/500G/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
jar tf legado-server/build/libs/legado-server-all.jar | rg 'web/(index\.html|assets/(BookShelf|BookChapter|index)-)'
```

Expected: `sha256sum` prints one SHA-256 line. `jar tf` lists `web/index.html` plus current `BookShelf-*.js`, `BookChapter-*.js`, and `index-*.js` assets.

- [ ] **Step 5: Commit generated resources**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git add legado-server/src/main/resources/web
git commit -m "build: refresh embedded web assets"
```

- [ ] **Step 6: Final clean verification**

Run:

```bash
cd /run/media/allen/500G/projects/legado
git status --short
```

Expected: no tracked files are modified. The pre-existing untracked `.claude/` directory may still appear and must not be removed as part of this feature.
