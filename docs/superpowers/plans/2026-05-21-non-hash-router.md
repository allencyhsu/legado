# Non Hash Router Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Vue frontend from hash routing to real browser history URLs so chapter progress appears in the URL path/query that browsers, reloads, and Nginx access logs can observe.

**Architecture:** Replace all Vue Router instances with `createWebHistory()` and change native chapter links from `#/chapter?...` to `/chapter?...`. Preserve existing non-chapter query parameters such as Nginx `token` while programmatic navigation and progress sync overwrite only the reading-state query keys. Add a Ktor SPA fallback for real frontend routes so direct loads of `/chapter`, `/bookSource`, and `/rssSource` return the embedded `web/index.html` instead of 404.

**Tech Stack:** Vue 3, Vue Router 4 history mode, Vite, Pinia, Node smoke-test scripts, Ktor 3 static resources, Gradle shadowJar.

---

## File Structure

- Modify: `modules/web/src/router/index.ts`
  - Use `createWebHistory()` for the main Vite app router.

- Modify: `modules/web/src/router/bookRouter.ts`
  - Use `createWebHistory()` for the legacy bookshelf entry router.

- Modify: `modules/web/src/router/sourceRouter.ts`
  - Use `createWebHistory()` for the legacy source editor entry router.

- Modify: `modules/web/src/utils/chapterLink.ts`
  - Return real URLs such as `/chapter?token=...&bookUrl=...&chapterIndex=54`.
  - Preserve non-reading query parameters, especially `token`, from either Vue route query or `window.location.search`.
  - Keep reading keys authoritative from the selected book.

- Modify: `modules/web/src/views/BookShelf.vue`
  - Add `useRoute()`.
  - Pass `route.query` into `getChapterQuery()` for programmatic chapter navigation.

- Modify: `modules/web/src/views/BookChapter.vue`
  - Pass `route.query` into `getChapterQuery()` when syncing progress with `router.replace()`.

- Create: `modules/web/scripts/check-history-router.mjs`
  - Guard against reintroducing `createWebHashHistory`.
  - Guard that chapter hrefs use `/chapter?` instead of `#/chapter?`.
  - Guard that token/query preservation remains wired.

- Modify: `modules/web/scripts/check-mobile-book-activation.mjs`
  - Update the existing route-query assertion to expect `getChapterQuery(nextReadingBook, route.query)`.

- Modify: `modules/web/scripts/check-reading-state-resilience.mjs`
  - Update the existing route-sync assertion to expect `getChapterQuery(store.readingBook, route.query)`.

- Modify: `modules/web/package.json`
  - Add `test:history-router`.

- Modify: `AGENTS.md`
  - Add `npm run test:history-router` to the frontend packaging sequence.

- Modify: `legado-server/src/main/kotlin/io/legado/server/Application.kt`
  - Extract application setup into a testable `configureLegadoServer()` function.
  - Add SPA fallback routes for real frontend paths.

- Create: `legado-server/src/test/kotlin/io/legado/server/ApplicationRoutingTest.kt`
  - Verify `/chapter?...`, `/bookSource`, and `/rssSource` return frontend HTML.
  - Verify API routes still return JSON and are not swallowed by the SPA fallback.

- Generated after build: `legado-server/src/main/resources/web/`
  - Refresh from `modules/web/dist/` before producing the server JAR.

---

### Task 1: Add History Router Smoke Test

**Files:**
- Create: `modules/web/scripts/check-history-router.mjs`
- Modify: `modules/web/package.json`

- [ ] **Step 1: Create the failing history-router guard**

Create `modules/web/scripts/check-history-router.mjs` with this content:

```js
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const routerIndex = read('src/router/index.ts')
const bookRouter = read('src/router/bookRouter.ts')
const sourceRouter = read('src/router/sourceRouter.ts')
const chapterLink = read('src/utils/chapterLink.ts')
const bookShelf = read('src/views/BookShelf.vue')
const bookChapter = read('src/views/BookChapter.vue')

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

const assertNotContains = (content, pattern, message) => {
  if (pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

for (const [name, content] of [
  ['src/router/index.ts', routerIndex],
  ['src/router/bookRouter.ts', bookRouter],
  ['src/router/sourceRouter.ts', sourceRouter],
]) {
  assertContains(
    content,
    /createWebHistory\(\)/,
    `${name} must use createWebHistory() so chapter URLs are visible before the hash fragment.`,
  )
  assertNotContains(
    content,
    /createWebHashHistory/,
    `${name} must not use createWebHashHistory().`,
  )
}

assertContains(
  chapterLink,
  /return\s+`\/chapter\?\$\{query\.toString\(\)\}`/,
  'Chapter native hrefs must use /chapter?... instead of #/chapter?...',
)

assertNotContains(
  chapterLink,
  /#\/chapter/,
  'Chapter native hrefs must not use hash router URLs.',
)

assertContains(
  chapterLink,
  /getPreservedRouteQuery/,
  'chapterLink must preserve non-reading route query parameters such as token.',
)

assertContains(
  bookShelf,
  /const route = useRoute\(\)/,
  'BookShelf must read the current route so token query parameters survive programmatic navigation.',
)

assertContains(
  bookShelf,
  /query:\s*getChapterQuery\(nextReadingBook,\s*route\.query\)/,
  'BookShelf programmatic chapter navigation must preserve non-reading query parameters.',
)

assertContains(
  bookChapter,
  /query:\s*getChapterQuery\(store\.readingBook,\s*route\.query\)/,
  'BookChapter route progress sync must preserve non-reading query parameters.',
)

if (process.exitCode) process.exit(process.exitCode)
```

- [ ] **Step 2: Add the npm script**

In `modules/web/package.json`, add this script near the other test scripts:

```json
"test:history-router": "node scripts/check-history-router.mjs",
```

The scripts section should include:

```json
"test:mobile-layout": "node scripts/check-mobile-bookshelf-layout.mjs",
"test:mobile-activation": "node scripts/check-mobile-book-activation.mjs",
"test:reading-state": "node scripts/check-reading-state-resilience.mjs",
"test:history-router": "node scripts/check-history-router.mjs",
"test:cover-safety": "node scripts/check-cover-request-safety.mjs",
```

- [ ] **Step 3: Run the new test and verify it fails**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:history-router
```

Expected: FAIL with messages mentioning `createWebHistory()`, `/chapter?...`, and query preservation.

---

### Task 2: Switch Vue Routers to Browser History

**Files:**
- Modify: `modules/web/src/router/index.ts`
- Modify: `modules/web/src/router/bookRouter.ts`
- Modify: `modules/web/src/router/sourceRouter.ts`

- [ ] **Step 1: Update the main app router**

Replace `modules/web/src/router/index.ts` with:

```ts
import { createWebHistory, createRouter } from 'vue-router'
import { bookRoutes } from './bookRouter'
import { sourceRoutes } from './sourceRouter'

const router = createRouter({
  history: createWebHistory(),
  routes: [bookRoutes, sourceRoutes].flat(),
})

router.afterEach(to => {
  if (to.name == 'shelf') document.title = '书架'
})

export default router
```

- [ ] **Step 2: Update the legacy bookshelf router**

Replace `modules/web/src/router/bookRouter.ts` with:

```ts
import { createWebHistory, createRouter } from 'vue-router'

export const bookRoutes = [
  {
    path: '/',
    name: 'shelf',
    component: () => import('../views/BookShelf.vue'),
  },
  {
    path: '/chapter',
    name: 'chapter',
    component: () => import('../views/BookChapter.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: bookRoutes,
})

export default router
```

- [ ] **Step 3: Update the legacy source router**

Replace `modules/web/src/router/sourceRouter.ts` with:

```ts
import sourceEditor from '../views/SourceEditor.vue'
import { createWebHistory, createRouter } from 'vue-router'

export const sourceRoutes = [
  {
    path: '/bookSource',
    name: 'book-home',
    component: sourceEditor,
  },
  {
    path: '/rssSource',
    name: 'rss-home',
    component: sourceEditor,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: sourceRoutes,
})

export default router
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:history-router
```

Expected: still FAIL because `chapterLink.ts`, `BookShelf.vue`, and `BookChapter.vue` have not been updated yet. The router-specific failures should be gone.

---

### Task 3: Convert Chapter Links and Preserve Token Query

**Files:**
- Modify: `modules/web/src/utils/chapterLink.ts`
- Modify: `modules/web/src/views/BookShelf.vue`
- Modify: `modules/web/src/views/BookChapter.vue`

- [ ] **Step 1: Replace the chapter link helper**

Replace `modules/web/src/utils/chapterLink.ts` with:

```ts
import type { BaseBook } from '@/book'
import type { LocationQueryRaw, LocationQueryValueRaw } from 'vue-router'

type ChapterLinkBook = Partial<BaseBook> & {
  durChapterIndex?: number
  durChapterPos?: number
  chapterIndex?: number
  chapterPos?: number
  isSeachBook?: boolean
  respondTime?: unknown
}

const CHAPTER_QUERY_KEYS = new Set([
  'bookUrl',
  'bookName',
  'bookAuthor',
  'chapterIndex',
  'chapterPos',
  'isSeachBook',
])

const toQueryValue = (value: unknown): LocationQueryValueRaw | undefined => {
  if (value === undefined) return undefined
  if (value === null) return null
  return String(value)
}

const getWindowQuery = (): LocationQueryRaw => {
  if (typeof window === 'undefined') return {}
  return Object.fromEntries(new URLSearchParams(window.location.search).entries())
}

export const getPreservedRouteQuery = (
  query: Record<string, unknown> = {},
): LocationQueryRaw => {
  const preserved: LocationQueryRaw = {}
  for (const [key, value] of Object.entries(query)) {
    if (CHAPTER_QUERY_KEYS.has(key)) continue
    if (Array.isArray(value)) {
      const values = value
        .map(toQueryValue)
        .filter((item): item is LocationQueryValueRaw => item !== undefined)
      if (values.length > 0) preserved[key] = values
      continue
    }
    const nextValue = toQueryValue(value)
    if (nextValue !== undefined) preserved[key] = nextValue
  }
  return preserved
}

const toSearchParams = (query: LocationQueryRaw) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null) params.append(key, String(item))
      }
      continue
    }
    if (value !== undefined && value !== null) params.set(key, String(value))
  }
  return params
}

export const getChapterHref = (
  book: ChapterLinkBook,
  preservedQuery: Record<string, unknown> = getWindowQuery(),
) => {
  const query = toSearchParams(getChapterQuery(book, preservedQuery))
  return `/chapter?${query.toString()}`
}

export const getChapterQuery = (
  book: ChapterLinkBook,
  preservedQuery: Record<string, unknown> = getWindowQuery(),
): LocationQueryRaw => {
  return {
    ...getPreservedRouteQuery(preservedQuery),
    bookUrl: book.bookUrl ?? '',
    bookName: book.name ?? '',
    bookAuthor: book.author ?? '',
    chapterIndex: String(book.durChapterIndex ?? book.chapterIndex ?? 0),
    chapterPos: String(book.durChapterPos ?? book.chapterPos ?? 0),
    isSeachBook: String(book.isSeachBook ?? 'respondTime' in book),
  }
}
```

- [ ] **Step 2: Preserve route query in BookShelf navigation**

In `modules/web/src/views/BookShelf.vue`, below the existing store setup:

```ts
const store = useBookStore()
const route = useRoute()
const isNight = computed(() => store.isNight)
```

Change the programmatic chapter navigation from:

```ts
router.push({
  path: '/chapter',
  query: getChapterQuery(nextReadingBook),
})
```

to:

```ts
router.push({
  path: '/chapter',
  query: getChapterQuery(nextReadingBook, route.query),
})
```

- [ ] **Step 3: Preserve route query in BookChapter progress sync**

In `modules/web/src/views/BookChapter.vue`, change:

```ts
const syncChapterRouteProgress = () => {
  router.replace({
    path: '/chapter',
    query: getChapterQuery(store.readingBook),
  })
}
```

to:

```ts
const syncChapterRouteProgress = () => {
  router.replace({
    path: '/chapter',
    query: getChapterQuery(store.readingBook, route.query),
  })
}
```

- [ ] **Step 4: Run type check**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run type-check
```

Expected: PASS with no TypeScript errors.

- [ ] **Step 5: Run the focused history-router test**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:history-router
```

Expected: PASS.

---

### Task 4: Update Existing Frontend Guards for Preserved Query

**Files:**
- Modify: `modules/web/scripts/check-mobile-book-activation.mjs`
- Modify: `modules/web/scripts/check-reading-state-resilience.mjs`

- [ ] **Step 1: Update mobile activation route assertion**

In `modules/web/scripts/check-mobile-book-activation.mjs`, replace:

```js
assertContains(
  bookShelf,
  /router\.push\(\{\s*path: ['"]\/chapter['"],\s*query: getChapterQuery\(nextReadingBook\),\s*\}\)/,
  'Programmatic chapter navigation must carry the same query data as native href navigation.',
)
```

with:

```js
assertContains(
  bookShelf,
  /router\.push\(\{\s*path: ['"]\/chapter['"],\s*query: getChapterQuery\(nextReadingBook,\s*route\.query\),\s*\}\)/,
  'Programmatic chapter navigation must carry the same reading query data and preserve token query parameters.',
)
```

- [ ] **Step 2: Update reading-state route assertions**

In `modules/web/scripts/check-reading-state-resilience.mjs`, replace:

```js
assertContains(
  bookShelf,
  /store\.setReadingBook\(nextReadingBook\)[\s\S]*router\.push\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(nextReadingBook\),\s*\}\)/,
  'BookShelf must put the selected book in Pinia and route query before routing to /chapter.',
)
```

with:

```js
assertContains(
  bookShelf,
  /store\.setReadingBook\(nextReadingBook\)[\s\S]*router\.push\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(nextReadingBook,\s*route\.query\),\s*\}\)/,
  'BookShelf must put the selected book in Pinia, preserve non-reading query parameters, and route to /chapter.',
)
```

Then replace:

```js
assertContains(
  bookChapter,
  /router\.replace\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(store\.readingBook\),\s*\}\)/,
  'BookChapter must replace the current chapter route query when reading progress changes.',
)
```

with:

```js
assertContains(
  bookChapter,
  /router\.replace\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(store\.readingBook,\s*route\.query\),\s*\}\)/,
  'BookChapter must replace the current chapter route query while preserving non-reading query parameters.',
)
```

- [ ] **Step 3: Run affected frontend guards**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:mobile-activation
npm run test:reading-state
npm run test:history-router
```

Expected: all three commands PASS.

---

### Task 5: Add Server-Side SPA Fallback for Real Routes

**Files:**
- Modify: `legado-server/src/main/kotlin/io/legado/server/Application.kt`
- Create: `legado-server/src/test/kotlin/io/legado/server/ApplicationRoutingTest.kt`

- [ ] **Step 1: Refactor application setup for tests**

In `legado-server/src/main/kotlin/io/legado/server/Application.kt`, add this function above `fun main(args: Array<String>)`:

```kotlin
fun Application.configureLegadoServer(
    bookService: BookService,
    httpClient: HttpClient,
    ttsUrl: String
) {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
            disableHtmlEscaping()
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }

    install(CallLogging)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ReturnData.error(cause.message ?: "Unknown error")
            )
        }
    }

    routing {
        bookRoutes(bookService)
        progressRoutes(bookService)
        ttsRoutes(httpClient, ttsUrl)

        staticResources("/", "web") {
            default("index.html")
        }

        get("/chapter") {
            call.respondResource("web/index.html")
        }
        get("/bookSource") {
            call.respondResource("web/index.html")
        }
        get("/rssSource") {
            call.respondResource("web/index.html")
        }
    }
}
```

Then replace the whole `embeddedServer(Netty, port = port) { ... }.start(wait = true)` block in `main()` with:

```kotlin
embeddedServer(Netty, port = port) {
    configureLegadoServer(bookService, httpClient, ttsUrl)
}.start(wait = true)
```

- [ ] **Step 2: Confirm imports still compile**

Keep these imports in `Application.kt` because the new extracted function uses them:

```kotlin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
```

- [ ] **Step 3: Add routing regression tests**

Create `legado-server/src/test/kotlin/io/legado/server/ApplicationRoutingTest.kt` with:

```kotlin
package io.legado.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.legado.server.service.BookService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationRoutingTest {
    @Test
    fun `chapter route returns vue app for direct browser history loads`() = testApplication {
        val booksDir = Files.createTempDirectory("legado-routing-test")
        application {
            configureLegadoServer(
                BookService(booksDir.toString()),
                HttpClient(CIO),
                "http://127.0.0.1:65535"
            )
        }

        val response = client.get("/chapter?token=abc&bookUrl=file%3A%2F%2Fbook.txt&chapterIndex=54")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("text/html"))
    }

    @Test
    fun `source routes return vue app for direct browser history loads`() = testApplication {
        val booksDir = Files.createTempDirectory("legado-routing-test")
        application {
            configureLegadoServer(
                BookService(booksDir.toString()),
                HttpClient(CIO),
                "http://127.0.0.1:65535"
            )
        }

        assertEquals(HttpStatusCode.OK, client.get("/bookSource").status)
        assertEquals(HttpStatusCode.OK, client.get("/rssSource").status)
    }

    @Test
    fun `api routes are not handled by spa fallback`() = testApplication {
        val booksDir = Files.createTempDirectory("legado-routing-test")
        application {
            configureLegadoServer(
                BookService(booksDir.toString()),
                HttpClient(CIO),
                "http://127.0.0.1:65535"
            )
        }

        val response = client.get("/getChapterList")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("application/json"))
    }
}
```

- [ ] **Step 4: Run server tests**

Run:

```bash
cd /home/allen/projects/legado/legado-server
./gradlew test
```

Expected: BUILD SUCCESSFUL.

---

### Task 6: Update AGENTS Packaging Instructions

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Add the new frontend guard to the packaging sequence**

In `AGENTS.md`, change the frontend sequence from:

```bash
npm run test:reading-state
npm run test:cover-safety
npm run build-only
```

to:

```bash
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
npm run build-only
```

- [ ] **Step 2: Verify the response rule remains present**

Run:

```bash
cd /home/allen/projects/legado
rg -n 'Every assistant response must include the name "Allen"\\.|npm run test:history-router' AGENTS.md
```

Expected: output contains one line for the Allen response rule and one line for `npm run test:history-router`.

---

### Task 7: Full Build, Embed Frontend, and Package JAR

**Files:**
- Generated: `modules/web/dist/`
- Modify generated assets: `legado-server/src/main/resources/web/`
- Generated: `legado-server/build/libs/legado-server-all.jar`

- [ ] **Step 1: Run all frontend checks**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:reading-state
npm run test:history-router
npm run test:cover-safety
```

Expected: all commands PASS.

- [ ] **Step 2: Build frontend**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run build-only
```

Expected: Vite prints `✓ built` and writes hashed assets under `modules/web/dist/assets/`.

- [ ] **Step 3: Sync frontend into server resources**

Run:

```bash
cd /home/allen/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/
```

Expected: `git status --short` shows updated generated assets under `legado-server/src/main/resources/web/`.

- [ ] **Step 4: Run server tests and build the standalone JAR**

Run:

```bash
cd /home/allen/projects/legado/legado-server
./gradlew test shadowJar
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Confirm JAR checksum and embedded frontend**

Run:

```bash
cd /home/allen/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
jar tf legado-server/build/libs/legado-server-all.jar | rg 'web/(index\\.html|assets/(BookChapter|BookShelf|index)-)'
```

Expected: checksum prints one SHA-256 line and `jar tf` lists `web/index.html` plus current hashed frontend chunks.

---

### Task 8: Deploy and Verify Real URLs Through Nginx

**Files:**
- Deploy artifact: `legado-server/build/libs/legado-server-all.jar`

- [ ] **Step 1: Upload the rebuilt JAR**

Run:

```bash
cd /home/allen/projects/legado
scp legado-server/build/libs/legado-server-all.jar allen@800g4:/tmp/legado-server-all.jar.new
ssh allen@800g4 'sha256sum /tmp/legado-server-all.jar.new'
```

Expected: remote checksum matches local checksum from Task 7.

- [ ] **Step 2: Install and restart on `800g4`**

Run:

```bash
ssh allen@800g4 'sudo install -o legado -g legado -m 0644 /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar && sudo systemctl restart legado-server && sha256sum /opt/legado-server/legado-server-all.jar && systemctl status legado-server --no-pager -l'
```

Expected: checksum matches the upload and `legado-server.service` is `active (running)`.

- [ ] **Step 3: Verify browser-history routes return the Vue app locally on the remote**

Run:

```bash
ssh allen@800g4 'curl -I "http://127.0.0.1:8081/chapter?token=probe&bookUrl=test&chapterIndex=54"'
```

Expected: HTTP status is `200 OK` and `Content-Type` is HTML.

- [ ] **Step 4: Verify Nginx forwards real frontend routes**

Run:

```bash
curl -I "https://anvl.metahubs.uk/chapter?token=probe&bookUrl=test&chapterIndex=54"
```

Expected: HTTP status is not `404`; for token rejection it may be `401` or `403`, and for accepted token it should be `200`. A `404` here means the Docker Nginx config must add an SPA fallback such as `try_files $uri $uri/ /index.html;` or proxy `/chapter`, `/bookSource`, and `/rssSource` to the Legado backend.

- [ ] **Step 5: Verify access logs show real chapter URLs**

After opening a valid mobile URL and advancing from chapter 39 to 54, run:

```bash
ssh allen@800g4 'docker logs nginx --since 10m 2>&1 | rg "GET /chapter\\?[^ ]*chapterIndex=(39|54)|GET /assets/.*\\.js|GET /getBookContent"'
```

Expected: logs show `GET /chapter?...chapterIndex=54...` or at least the accepted real route before API calls. If logs still show only `/?token=...`, the phone is loading an old bookmarked hash URL or cached frontend chunk.

---

## Self-Review

- Spec coverage: The plan switches all known Vue routers away from hash history, converts native chapter hrefs, preserves Nginx `token`, adds server fallback for direct real-route loads, updates frontend packaging instructions, packages the embedded frontend, and includes deployment verification for `allen@800g4`.
- Placeholder scan: No implementation step depends on an unspecified helper or unnamed test. Every new file has complete content.
- Type consistency: `getChapterQuery(book, route.query)` returns `LocationQueryRaw`, which is valid for Vue Router `router.push()` and `router.replace()`. `getChapterHref()` converts the same query into `URLSearchParams` for native anchors.
