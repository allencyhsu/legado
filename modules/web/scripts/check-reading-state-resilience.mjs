import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const bookShelf = read('src/views/BookShelf.vue')
const bookChapter = read('src/views/BookChapter.vue')
const axios = read('src/api/axios.ts')

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

assertContains(
  bookShelf,
  /store\.setReadingBook\(nextReadingBook\)[\s\S]*router\.push\(\{\s*path:\s*['"]\/chapter['"],\s*query:\s*getChapterQuery\(nextReadingBook\),\s*\}\)/,
  'BookShelf must put the selected book in Pinia and route query before routing to /chapter.',
)

assertContains(
  bookShelf,
  /setSessionStorageItem\(['"]bookUrl['"]/,
  'BookShelf must use safe sessionStorage writes for selected book state.',
)

assertContains(
  bookChapter,
  /resolveReadingBook\(\)/,
  'BookChapter must resolve reading state through a fallback helper.',
)

assertContains(
  bookChapter,
  /useRoute\(\)/,
  'BookChapter must inspect route query as a native-link fallback for mobile browsers.',
)

assertContains(
  bookChapter,
  /queryString\(['"]bookUrl['"]\)/,
  'BookChapter must recover selected bookUrl from the chapter route query.',
)

assertContains(
  bookChapter,
  /parseReadingRecent\(\)/,
  'BookChapter must fall back to the persisted recent book when sessionStorage is missing.',
)

assertContains(
  bookChapter,
  /getSessionStorageItem\(['"]bookUrl['"]/,
  'BookChapter must use safe sessionStorage reads for selected book state.',
)

assertContains(
  axios,
  /getLocalStorageItem\(baseURL_localStorage_key\)/,
  'Axios baseURL initialization must tolerate mobile browsers where localStorage throws.',
)

if (process.exitCode) process.exit(process.exitCode)
