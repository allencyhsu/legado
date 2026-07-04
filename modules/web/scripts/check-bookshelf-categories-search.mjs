import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { pathToFileURL } from 'node:url'
import { build } from 'esbuild'

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
const bookShelf = read('src/views/BookShelf.vue')
const bookItems = read('src/components/BookItems.vue')
const helperPath = path.join(root, 'src/utils/bookshelfGrouping.ts')
const bundleDir = fs.mkdtempSync(path.join(os.tmpdir(), 'legado-bookshelf-grouping-'))
const bundledHelperPath = path.join(bundleDir, 'bookshelfGrouping.mjs')

await build({
  entryPoints: [helperPath],
  bundle: true,
  format: 'esm',
  platform: 'node',
  outfile: bundledHelperPath,
})

const {
  filterBookshelfBooks,
  groupBookshelfBooks,
  normalizeBookshelfSearch,
} = await import(`${pathToFileURL(bundledHelperPath).href}?t=${Date.now()}`)

const assert = (condition, message) => {
  if (!condition) {
    console.error(message)
    process.exitCode = 1
  }
}

const assertEqual = (actual, expected, message) => {
  if (actual !== expected) {
    console.error(`${message}\nExpected: ${expected}\nActual: ${actual}`)
    process.exitCode = 1
  }
}

const books = [
  {
    name: '乱世国医',
    author: '陳醫生',
    kind: '历史',
    originName: '本地《典藏》',
    bookUrl: 'https://shelf.example/books/luan-shi-guo-yi',
  },
  {
    name: '后台时代',
    author: '后勤作者',
    kind: '技术',
    originName: '雲端書庫',
    bookUrl: 'https://shelf.example/books/hou-tai-shi-dai',
  },
  {
    name: '體育周刊',
    author: '王教練',
    kind: '運動',
    originName: '體育館藏',
    bookUrl: 'https://shelf.example/books/ti-yu-zhou-kan',
  },
  {
    name: '《Punctuation-Test》',
    author: 'Case-Sensitive Author',
    kind: 'Reference',
    originName: 'Archive:Alpha',
    bookUrl: 'https://shelf.example/books/punctuation_test',
  },
  {
    name: 'Fallback Book',
    author: '',
    kind: '',
    originName: 'Unknown Shelf',
    bookUrl: 'https://shelf.example/books/fallback',
  },
]

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
  /from\s+['"]opencc-js['"]/,
  'Search normalization must use opencc-js for comprehensive simplified/traditional normalization.',
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
  packageJson,
  /"opencc-js": "1\.4\.0"/,
  'package.json must include opencc-js 1.4.0 for simplified/traditional normalization.',
)

assertContains(
  packageJson,
  /"esbuild": "\^0\.21\.5"/,
  'package.json must declare esbuild as a direct devDependency because the bookshelf category test imports it directly.',
)

assertContains(
  agents,
  /npm run test:bookshelf-categories/,
  'AGENTS.md packaging sequence must include npm run test:bookshelf-categories.',
)

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
  /let suppressSearchWordReset = false/,
  'BookShelf.vue must keep a suppression flag for programmatic recent-book searches.',
)

assertContains(
  bookShelf,
  /watch\(searchWord, \(\) => \{[\s\S]*?if \(suppressSearchWordReset\) \{[\s\S]*?suppressSearchWordReset = false[\s\S]*?return[\s\S]*?\}/,
  'BookShelf.vue must skip clearing online search state once when searchWord is changed programmatically.',
)

assertContains(
  bookShelf,
  /fromReadRecentClick[\s\S]*?suppressSearchWordReset = true[\s\S]*?searchWord\.value = bookName[\s\S]*?searchBook\(\)/,
  'Recent-book auto search must set the suppression flag before assigning searchWord and starting the online search.',
)

assertContains(
  bookShelf,
  /fromReadRecentClick[\s\S]*?searchWord\.value = bookName[\s\S]*?searchBook\(\)[\s\S]*?nextTick\(\(\) => \{[\s\S]*?suppressSearchWordReset = false[\s\S]*?\}\)/,
  'Recent-book auto search must explicitly clear the suppression flag after the one-shot online search so same-value assignments cannot leak it.',
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
  bookShelf,
  /@media\s+screen\s+and\s+\(max-width:\s*750px\)[\s\S]*\.shelf-wrapper\s*\{[\s\S]*overflow:\s*auto;[\s\S]*\.grouped-shelf\s*\{[\s\S]*overflow:\s*visible;/,
  'Mobile grouped shelf must not create a second vertical scroll container.',
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

assertEqual(
  normalizeBookshelfSearch('《亂世：國醫》'),
  '乱世国医',
  'normalizeBookshelfSearch must strip punctuation and canonicalize traditional text to simplified search form.',
)

assertEqual(
  filterBookshelfBooks(books, '亂世國醫').length,
  1,
  'Local bookshelf search must match traditional query 亂世國醫 against simplified title 乱世国医.',
)

assertEqual(
  filterBookshelfBooks(books, '後台時代').length,
  1,
  'Local bookshelf search must match traditional query 後台時代 against simplified title 后台时代.',
)

assertEqual(
  filterBookshelfBooks(books, '体育周刊').length,
  1,
  'Local bookshelf search must match simplified query 体育周刊 against traditional title 體育周刊.',
)

assertEqual(
  filterBookshelfBooks(books, 'case sensitive author').length,
  1,
  'Local bookshelf search must match authors case-insensitively.',
)

assertEqual(
  filterBookshelfBooks(books, 'reference').length,
  1,
  'Local bookshelf search must match category names.',
)

assertEqual(
  filterBookshelfBooks(books, 'archivealpha').length,
  1,
  'Local bookshelf search must match originName with punctuation removed.',
)

assertEqual(
  filterBookshelfBooks(books, 'punctuationtest').length,
  1,
  'Local bookshelf search must match title text with punctuation removed.',
)

assertEqual(
  filterBookshelfBooks(books, 'ti-yu-zhou-kan').length,
  1,
  'Local bookshelf search must match bookUrl content.',
)

const groupedBooks = groupBookshelfBooks(books)
const fallbackGroup = groupedBooks.find(group => group.kind === '未分類')
const fallbackAuthorGroup = fallbackGroup?.authorGroups.find(
  authorGroup => authorGroup.author === '未知作者',
)

assert(Boolean(fallbackGroup), 'groupBookshelfBooks must use 未分類 for missing kind values.')
assert(Boolean(fallbackAuthorGroup), 'groupBookshelfBooks must use 未知作者 for missing author values.')
assertEqual(
  fallbackAuthorGroup?.count,
  1,
  'Fallback author groups must preserve the underlying book count.',
)

if (process.exitCode) process.exit(process.exitCode)
