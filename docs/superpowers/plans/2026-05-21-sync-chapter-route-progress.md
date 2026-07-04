# Sync Chapter Route Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the browser URL query in sync with the actual chapter/position progress so reopening the browser resumes at the latest chapter instead of an old bookmarked query.

**Architecture:** `BookChapter.vue` already resolves reading state from route query first, then session/Pinia/localStorage. The fix is to update the route query whenever persisted reading progress changes, using `router.replace()` so chapter navigation does not spam browser history entries. Existing `getChapterQuery()` remains the shared query serializer used by both bookshelf entry links and chapter progress updates.

**Tech Stack:** Vue 3 Composition API, Vue Router hash history, Pinia, Vite, Node script smoke tests.

---

## File Structure

- Modify: `modules/web/src/views/BookChapter.vue`
  - Import `getChapterQuery`.
  - Add a route-sync helper that updates `/chapter` query with the current `store.readingBook` progress.
  - Call the helper from `saveReadingBookProgressToBrowser()` after updating `chapterIndex` and `chapterPos`.
  - Use `router.replace()`, not `router.push()`, to keep Back behavior sane.

- Modify: `modules/web/scripts/check-reading-state-resilience.mjs`
  - Extend the existing resilience check so it verifies chapter progress updates synchronize route query with `router.replace()`.

- Generated after build: `legado-server/src/main/resources/web/`
  - Must be refreshed from `modules/web/dist/` because `legado-server` serves embedded static assets.

---

### Task 1: Add Failing Route-Sync Guard

**Files:**
- Modify: `modules/web/scripts/check-reading-state-resilience.mjs`

- [ ] **Step 1: Write the failing test**

Add these assertions after the existing `BookChapter must recover selected bookUrl from the chapter route query.` assertion:

```js
assertContains(
  bookChapter,
  /import\s+\{\s*getChapterQuery\s*\}\s+from\s+['"]@\/utils\/chapterLink['"]/,
  'BookChapter must reuse getChapterQuery when synchronizing reading progress to the URL.',
)

assertContains(
  bookChapter,
  /router\.replace\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(store\.readingBook\),\s*\}\)/,
  'BookChapter must replace the current chapter route query when reading progress changes.',
)

assertContains(
  bookChapter,
  /const saveReadingBookProgressToBrowser[\s\S]*chapterIndex\.value\s*=\s*index[\s\S]*chapterPos\.value\s*=\s*pos[\s\S]*syncChapterRouteProgress\(\)/,
  'BookChapter must sync the route after updating Pinia chapter progress.',
)
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:reading-state
```

Expected: FAIL with these messages:

```text
BookChapter must reuse getChapterQuery when synchronizing reading progress to the URL.
BookChapter must replace the current chapter route query when reading progress changes.
BookChapter must sync the route after updating Pinia chapter progress.
```

- [ ] **Step 3: Commit the failing test**

Do not commit the failing test alone. Keep it staged/unstaged for Task 2, then commit test and implementation together after green.

---

### Task 2: Sync Progress Into URL Query

**Files:**
- Modify: `modules/web/src/views/BookChapter.vue`
- Modify: `modules/web/scripts/check-reading-state-resilience.mjs`

- [ ] **Step 1: Import shared query serializer**

In `modules/web/src/views/BookChapter.vue`, near the existing utility imports, add:

```ts
import { getChapterQuery } from '@/utils/chapterLink'
```

- [ ] **Step 2: Add the route-sync helper**

Below `const router = useRouter()`, add:

```ts
const syncChapterRouteProgress = () => {
  router.replace({
    path: '/chapter',
    query: getChapterQuery(store.readingBook),
  })
}
```

Use `replace()` intentionally. The expected behavior is that the address bar tracks the latest chapter without adding one history entry per chapter switch.

- [ ] **Step 3: Call the helper after progress changes**

Change `saveReadingBookProgressToBrowser()` from:

```ts
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  // 保存pinia
  chapterIndex.value = index
  chapterPos.value = pos
}
```

to:

```ts
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  // 保存pinia
  chapterIndex.value = index
  chapterPos.value = pos
  syncChapterRouteProgress()
}
```

- [ ] **Step 4: Run focused test**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run test:reading-state
```

Expected: PASS.

- [ ] **Step 5: Run type check**

Run:

```bash
cd /home/allen/projects/legado/modules/web
npm run type-check
```

Expected: PASS with no TypeScript errors.

- [ ] **Step 6: Commit**

```bash
cd /home/allen/projects/legado
git add modules/web/src/views/BookChapter.vue modules/web/scripts/check-reading-state-resilience.mjs
git commit -m "Sync chapter progress to route query"
```

---

### Task 3: Full Frontend Verification and Server Repack

**Files:**
- Generated: `modules/web/dist/`
- Modify generated assets: `legado-server/src/main/resources/web/`

- [ ] **Step 1: Run all frontend checks**

```bash
cd /home/allen/projects/legado/modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:reading-state
npm run test:cover-safety
```

Expected: all commands PASS.

- [ ] **Step 2: Build frontend**

```bash
cd /home/allen/projects/legado/modules/web
npm run build-only
```

Expected: Vite prints `✓ built` and new hashed assets under `modules/web/dist/assets/`.

- [ ] **Step 3: Sync frontend into server resources**

```bash
cd /home/allen/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/
```

Expected: `git status --short` shows updated generated assets under `legado-server/src/main/resources/web/`.

- [ ] **Step 4: Repack server JAR from the correct Gradle project**

```bash
cd /home/allen/projects/legado/legado-server
./gradlew test shadowJar
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify generated JAR contains new frontend chunks**

```bash
cd /home/allen/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
jar tf legado-server/build/libs/legado-server-all.jar | rg 'web/assets/(BookShelf|BookChapter|index)-|web/index.html'
```

Expected: checksum prints one SHA-256 line and `jar tf` lists the current generated `BookChapter-*.js`, `BookShelf-*.js`, `index-*.js`, and `web/index.html`.

- [ ] **Step 6: Run whitespace check**

```bash
cd /home/allen/projects/legado
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 7: Commit generated frontend assets**

```bash
cd /home/allen/projects/legado
git add legado-server/src/main/resources/web
git commit -m "Package synced chapter route frontend"
```

If Task 2 and Task 3 are executed in one uninterrupted session, it is acceptable to make one combined commit instead:

```bash
git add modules/web/src/views/BookChapter.vue modules/web/scripts/check-reading-state-resilience.mjs legado-server/src/main/resources/web
git commit -m "Sync chapter progress to route query"
```

---

### Task 4: Deploy and Verify on `allen@800g4`

**Files:**
- Deploy artifact: `legado-server/build/libs/legado-server-all.jar`

- [ ] **Step 1: Upload rebuilt JAR**

```bash
cd /home/allen/projects/legado
scp legado-server/build/libs/legado-server-all.jar allen@800g4:/tmp/legado-server-all.jar.new
ssh allen@800g4 'sha256sum /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar'
```

Expected: `/tmp/legado-server-all.jar.new` checksum matches the local checksum from Task 3, and `/opt/legado-server/legado-server-all.jar` may still show the old checksum before installation.

- [ ] **Step 2: Install and restart service on remote host**

Run on `800g4` because sudo requires an interactive password:

```bash
sudo install -o legado -g legado -m 0644 /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar
sudo systemctl restart legado-server
sha256sum /opt/legado-server/legado-server-all.jar
systemctl status legado-server --no-pager -l
```

Expected: installed checksum matches `/tmp/legado-server-all.jar.new`, and `legado-server.service` is `active (running)`.

- [ ] **Step 3: Browser behavior verification**

In the mobile browser:

1. Open `https://anvl.metahubs.uk/?token=<token>`.
2. Open a book currently at chapter 39.
3. Tap next chapter until chapter 54.
4. Confirm the address bar changes to `#/chapter?...chapterIndex=54...`.
5. Close the browser tab/app.
6. Reopen the same browser history entry.

Expected: the page opens chapter 54, not chapter 39.

- [ ] **Step 4: Verify Nginx loads new frontend**

```bash
ssh allen@800g4 'docker exec nginx sh -c "tail -n 220 /data/logs/proxy-host-8_access.log | grep -E \"BookChapter|BookShelf|index-|getChapterList|getBookContent\" | tail -n 80"'
```

Expected: logs show requests for the new hashed frontend chunks from Task 3 and chapter API requests for the newer chapter index.

---

## Self-Review

**Spec coverage:** The plan covers the reported problem where browser history keeps an old chapter query, the suspected URL not updating on next/previous chapter, automated guard coverage, frontend packaging, server JAR rebuild, and remote deployment verification.

**Placeholder scan:** No `TBD`, `TODO`, vague test steps, or omitted command details remain.

**Type consistency:** The plan uses existing names from the codebase: `store.readingBook`, `getChapterQuery`, `router.replace`, `saveReadingBookProgressToBrowser`, `chapterIndex`, and `chapterPos`.
