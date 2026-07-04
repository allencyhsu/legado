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

assertNotContains(
  bookItems,
  /class=["']cover-img["']/,
  'BookItems must not render a cover layout column.',
)

assertNotContains(
  bookItems,
  /class=["']cover["']/,
  'BookItems must not render cover image elements.',
)

assertNotContains(
  bookItems,
  /<img\b/,
  'BookItems must not render image elements after removing cover layout.',
)

assertNotContains(
  bookItems,
  /\bcoverUrl\b/,
  'BookItems must not read coverUrl after removing cover layout.',
)

assertNotContains(
  bookItems,
  /getCover|proxyImage|DEFAULT_COVER_SRC/,
  'BookItems must not keep cover image fallback logic after removing cover layout.',
)

assertNotContains(
  bookItems,
  /API\.getProxyCoverUrl|isLegadoUrl/,
  'BookItems must not request proxied cover URLs after removing cover layout.',
)

assertNotContains(
  bookItems,
  /coverUrl\s*===\s*undefined[\s\S]*API\.getProxyCoverUrl\(bookUrl\)/,
  'Books without coverUrl must not request /cover with bookUrl because local TXT files are huge.',
)

assertNotContains(
  bookItems,
  /height:\s*112px/,
  'BookItems must not keep the old fixed cover-height row layout.',
)

if (!packageJson.scripts?.['test:cover-safety']) {
  console.error('package.json must expose test:cover-safety.')
  process.exitCode = 1
}

if (process.exitCode) process.exit(process.exitCode)
