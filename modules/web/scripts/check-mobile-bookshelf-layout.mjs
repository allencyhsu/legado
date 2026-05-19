import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const bookShelf = read('src/views/BookShelf.vue')
const bookItems = read('src/components/BookItems.vue')

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

assertContains(
  bookShelf,
  /@media\s+screen\s+and\s+\(max-width:\s*750px\)[\s\S]*\.shelf-wrapper\s*\{[\s\S]*overflow:\s*auto;/,
  'Mobile bookshelf results must scroll instead of being clipped.',
)

assertContains(
  bookShelf,
  /@media\s+screen\s+and\s+\(max-width:\s*750px\)[\s\S]*\.shelf-wrapper\s*\{[\s\S]*min-height:\s*0;/,
  'Mobile bookshelf flex child needs min-height: 0 so its list can scroll.',
)

assertContains(
  bookItems,
  /\.books-wrapper\s*\{[\s\S]*height:\s*100%;[\s\S]*overflow:\s*auto;/,
  'Book list wrapper needs a stable height for mobile scrolling.',
)

assertContains(
  bookItems,
  /\.book\s*\{[\s\S]*touch-action:\s*manipulation;/,
  'Book rows should opt into direct touch activation on mobile browsers.',
)

if (process.exitCode) process.exit(process.exitCode)
