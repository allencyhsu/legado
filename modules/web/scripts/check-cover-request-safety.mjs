import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const bookItems = fs.readFileSync(
  path.join(root, 'src/components/BookItems.vue'),
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
  /const\s+DEFAULT_COVER_SRC\s*=/,
  'BookItems must define a local default cover for books without coverUrl.',
)

assertContains(
  bookItems,
  /coverUrl\s*===\s*undefined[\s\S]*DEFAULT_COVER_SRC/,
  'Books without coverUrl must use the local default cover.',
)

assertNotContains(
  bookItems,
  /coverUrl\s*===\s*undefined[\s\S]*API\.getProxyCoverUrl\(bookUrl\)/,
  'Books without coverUrl must not request /cover with bookUrl because local TXT files are huge.',
)

if (!packageJson.scripts?.['test:cover-safety']) {
  console.error('package.json must expose test:cover-safety.')
  process.exitCode = 1
}

if (process.exitCode) process.exit(process.exitCode)
