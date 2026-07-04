import type { Book } from '@/book'
import OpenCC from 'opencc-js'

export type BookshelfAuthorGroup = {
  author: string
  count: number
  books: Book[]
}

export type BookshelfCategoryGroup = {
  kind: string
  count: number
  authorGroups: BookshelfAuthorGroup[]
}

const SEARCH_PUNCTUATION = /[\s《》<>「」『』[\]【】()（）:：,，.。\-－_]/g
const toSimplified = OpenCC.Converter({ from: 'tw', to: 'cn' })
const normalizedSearchCache = new Map<string, string>()

export const normalizeBookshelfSearch = (value?: string): string => {
  const trimmedValue = (value ?? '').trim()
  if (trimmedValue === '') return ''

  const cachedValue = normalizedSearchCache.get(trimmedValue)
  if (cachedValue) return cachedValue

  const normalizedValue = toSimplified(trimmedValue)
    .toLowerCase()
    .replace(SEARCH_PUNCTUATION, '')

  normalizedSearchCache.set(trimmedValue, normalizedValue)
  return normalizedValue
}

export const bookMatchesLocalSearch = (book: Book, query: string): boolean => {
  const normalizedQuery = normalizeBookshelfSearch(query)
  if (normalizedQuery === '') return true

  return [book.name, book.author, book.kind, book.originName, book.bookUrl].some(
    value => normalizeBookshelfSearch(value).includes(normalizedQuery),
  )
}

export const filterBookshelfBooks = (books: Book[], query: string): Book[] =>
  books.filter(book => bookMatchesLocalSearch(book, query))

export const groupBookshelfBooks = (books: Book[]): BookshelfCategoryGroup[] => {
  const categoryMap = new Map<string, Map<string, Book[]>>()

  for (const book of books) {
    const kind = book.kind || '未分類'
    const author = book.author || '未知作者'

    if (!categoryMap.has(kind)) categoryMap.set(kind, new Map())
    const authorMap = categoryMap.get(kind)!
    if (!authorMap.has(author)) authorMap.set(author, [])
    authorMap.get(author)!.push(book)
  }

  return Array.from(categoryMap.entries()).map(([kind, authorMap]) => {
    const authorGroups = Array.from(authorMap.entries()).map(
      ([author, authorBooks]) => ({
        author,
        count: authorBooks.length,
        books: authorBooks,
      }),
    )

    return {
      kind,
      count: authorGroups.reduce((sum, group) => sum + group.count, 0),
      authorGroups,
    }
  })
}
