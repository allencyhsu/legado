# Remove Book Covers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the web bookshelf cover column because most local books do not have useful covers.

**Architecture:** `BookItems.vue` owns both local shelf rows and online search rows, so the cover UI should be removed there once. The cover safety script becomes a guard that prevents cover markup and cover proxy requests from returning.

**Tech Stack:** Vue 3, TypeScript, SCSS, Vite, Node script checks, Gradle shadow JAR packaging.

## Global Constraints

- Every assistant response must include the name "Allen".
- Frontend changes under `modules/web/` require the full AGENTS.md frontend packaging sequence.
- Do not run `./gradlew shadowJar` from the repository root; run it from `legado-server/`.
- Keep unrelated `.claude/` changes untouched.
- Do not commit unless Allen explicitly asks for a commit.

---

### Task 1: Cover Layout Removal

**Files:**
- Modify: `modules/web/scripts/check-cover-request-safety.mjs`
- Modify: `modules/web/src/components/BookItems.vue`
- Generated after build: `legado-server/src/main/resources/web/`

**Interfaces:**
- Consumes: `BookItems.vue` props `books`, `isSearch`, and `embedded`.
- Produces: Book rows with the same click, href, keyboard, and metadata behavior, but without cover DOM, cover fallback logic, or cover proxy requests.

- [ ] **Step 1: Write the failing test**

Update `modules/web/scripts/check-cover-request-safety.mjs` so it asserts that `BookItems.vue` does not contain `cover-img`, `cover`, `getCover`, `proxyImage`, `DEFAULT_COVER_SRC`, `API.getProxyCoverUrl`, or `isLegadoUrl`.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:cover-safety` from `modules/web/`

Expected: FAIL with messages about existing cover layout or cover logic in `BookItems.vue`.

- [ ] **Step 3: Write minimal implementation**

In `modules/web/src/components/BookItems.vue`, remove the cover wrapper and image from the template, remove unused cover imports and functions from script, and adjust row CSS so `.info` uses the full row width without a left margin.

- [ ] **Step 4: Run focused tests**

Run from `modules/web/`:

```bash
npm run test:cover-safety
npm run test:mobile-layout
npm run test:mobile-activation
```

Expected: all commands exit 0.

- [ ] **Step 5: Run full packaging sequence**

Run the exact AGENTS.md sequence from the repository root, including all frontend checks, `npm run build-only`, `rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/`, `./gradlew test shadowJar` from `legado-server/`, and `sha256sum legado-server/build/libs/legado-server-all.jar`.

Expected: all commands exit 0 and the embedded web resources contain the rebuilt hashed frontend assets.
