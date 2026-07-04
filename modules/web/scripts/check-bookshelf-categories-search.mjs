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
