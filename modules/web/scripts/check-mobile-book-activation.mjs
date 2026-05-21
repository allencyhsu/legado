import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const bookItems = fs.readFileSync(
  path.join(root, 'src/components/BookItems.vue'),
  'utf8',
)
const bookShelf = fs.readFileSync(
  path.join(root, 'src/views/BookShelf.vue'),
  'utf8',
)
const packageJsonPath = path.join(root, 'package.json')
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'))

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

assertContains(
  bookItems,
  /:href=["']getChapterHref\(book\)["']/,
  'Book rows must have a native chapter href so mobile browsers can navigate even when JS click handling is unreliable.',
)

assertContains(
  bookShelf,
  /:href=["']getChapterHref\(readingRecent\)["']/,
  'Recent reading entry must have a native chapter href for mobile browsers.',
)

assertContains(
  bookItems,
  /@pointerdown=["']handlePointerDown\(\$event,\s*book\)["']/,
  'Book rows must record pointer-down position for mobile tap activation.',
)

assertContains(
  bookItems,
  /@pointerup=["']handlePointerUp\(\$event,\s*book\)["']/,
  'Book rows must activate from pointer-up so iOS scroll containers do not swallow clicks.',
)

assertContains(
  bookItems,
  /@click=["']handleClick\(\$event,\s*book\)["']/,
  'Book rows must keep a click fallback for keyboard, desktop, and older browsers.',
)

assertNotContains(
  bookItems,
  /const handleClick[\s\S]*?event\.preventDefault\(\)\s*\n\s*activateBook\(book\)/,
  'Book row click handling must not block native href navigation for shelf books on mobile browsers.',
)

assertContains(
  bookItems,
  /'respondTime' in book[\s\S]*?event\.preventDefault\(\)/,
  'Search result clicks must still wait for JS saveBook handling before navigation.',
)

assertNotContains(
  bookShelf,
  /const handleRecentClick[\s\S]*?event\.preventDefault\(\)\s*\n\s*toDetail\(/,
  'Recent reading click handling must not block native href navigation on mobile browsers.',
)

assertContains(
  bookShelf,
  /router\.push\(\{\s*path: ['"]\/chapter['"],\s*query: getChapterQuery\(nextReadingBook,\s*route\.query\),\s*\}\)/,
  'Programmatic chapter navigation must carry the same reading query data and preserve token query parameters.',
)

assertContains(
  bookItems,
  /const\s+TAP_MOVEMENT_THRESHOLD\s*=\s*10/,
  'Book row pointer activation must have a movement threshold to avoid opening books while scrolling.',
)

assertContains(
  bookItems,
  /role=["']button["'][\s\S]*tabindex=["']0["']/,
  'Book rows must expose button semantics for mobile accessibility and keyboard fallback.',
)

if (!packageJson.scripts?.['test:mobile-activation']) {
  console.error('package.json must expose test:mobile-activation.')
  process.exitCode = 1
}

if (process.exitCode) process.exit(process.exitCode)
