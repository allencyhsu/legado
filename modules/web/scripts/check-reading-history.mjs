import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const api = read('src/api/api.ts')
const bookTypes = read('src/book.d.ts')
const bookStore = read('src/store/bookStore.ts')
const bookShelf = read('src/views/BookShelf.vue')
const setRemoteUrlBlock =
  bookShelf.match(
    /const setLegadoRetmoteUrl = \(\) => \{[\s\S]*?\n\}\n\nconst router = useRouter\(\)/,
  )?.[0] ?? ''

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
  /const loadReadingHistory = async \(\) => \{[\s\S]*?if \(isSuccess === true\) \{[\s\S]*?readingHistory\.value = data[\s\S]*?return[\s\S]*?\}[\s\S]*?readingHistory\.value = \[\][\s\S]*?ElMessage\.error\(errorMsg \|\| ['"]阅读历史加载失败['"]\)/,
  'BookShelf must clear stale server reading history before reporting a non-success load failure.',
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
  setRemoteUrlBlock,
  /setApiEntryPoint\([\s\S]*?(?:loadingWrapper\(loadShelf\(\)\)|loadReadingHistory\(\)|loadShelf\(\))/,
  'BookShelf backend URL changes must refresh reading history after switching the API entry point.',
)

assertContains(
  bookShelf,
  /removeLocalStorageItem\(['"]readingRecent['"]\)/,
  'BookShelf must clear the local readingRecent fallback when deleting matching history.',
)

assertContains(
  bookShelf,
  /type ReadingHistoryItem = \{[\s\S]*?fromLocalRecent: boolean[\s\S]*?\}/,
  'BookShelf reading history items must track whether they came from the local readingRecent fallback.',
)

assertContains(
  bookShelf,
  /const toHistoryItem = \(book: Book\): ReadingHistoryItem => \(\{[\s\S]*?fromLocalRecent: false,[\s\S]*?\}\)/,
  'BookShelf server reading history items must opt out of local fallback navigation mode.',
)

assertContains(
  bookShelf,
  /const toRecentHistoryItem = \(\): ReadingHistoryItem \| undefined => \{[\s\S]*?fromLocalRecent: true,[\s\S]*?\}/,
  'BookShelf local readingRecent fallback items must opt into fallback navigation mode.',
)

assertContains(
  bookShelf,
  /const openHistoryItem = \(item: ReadingHistoryItem, event\?: MouseEvent\) => \{[\s\S]*?item\.isSeachBook,[\s\S]*?item\.fromLocalRecent,[\s\S]*?\}/,
  'BookShelf history clicks must only use fallback navigation for local readingRecent items.',
)

assertContains(
  bookShelf,
  /const loadShelf = async \(\) => \{[\s\S]*?await store\.saveBookProgress\(\{\s*beacon:\s*false\s*\}\)[\s\S]*?await store\.loadBookShelf\(\)[\s\S]*?await loadReadingHistory\(\)[\s\S]*?\}/,
  'BookShelf loadShelf must await a non-beacon save before refreshing bookshelf and server reading history.',
)

assertContains(
  bookStore,
  /async saveBookProgress\(options: \{\s*beacon\?: boolean\s*\} = \{\}\) \{[\s\S]*?const \{ beacon = true \} = options[\s\S]*?if \(beacon\) \{[\s\S]*?return API\.saveBookProgressWithBeacon\(this\.bookProgress\)[\s\S]*?\}[\s\S]*?return API\.saveBookProgress\(this\.bookProgress\)[\s\S]*?\}/,
  'bookStore.saveBookProgress must support an awaitable non-beacon path while keeping beacon as the default behavior.',
)

assertContains(
  bookShelf,
  /@click\.stop\.prevent=["']deleteReadingHistory\(item\)["']/,
  'BookShelf single-item delete control must not open the book while deleting.',
)

if (process.exitCode) process.exit(process.exitCode)
