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

assertContains(
  bookChapter,
  /router\.push\(\{\s*path:\s*['"]\/['"],\s*query:\s*getPreservedRouteQuery\(route\.query\),\s*\}\)/,
  'BookChapter shelf navigation must preserve non-reading query parameters.',
)

if (process.exitCode) process.exit(process.exitCode)
