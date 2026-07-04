import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const api = read('src/api/api.ts')
const bookTypes = read('src/book.d.ts')
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
  /@click\.stop\.prevent=["']deleteReadingHistory\(item\)["']/,
  'BookShelf single-item delete control must not open the book while deleting.',
)

if (process.exitCode) process.exit(process.exitCode)
