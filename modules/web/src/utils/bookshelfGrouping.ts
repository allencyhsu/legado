import type { Book } from '@/book'

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
const SIMPLIFIED_TRADITIONAL_GROUPS: Array<readonly string[]> = [
  ['愛', '爱'],
  ['潛', '潜'],
  ['烏', '乌'],
  ['賊', '贼'],
  ['詭', '诡'],
  ['龍', '龙'],
  ['義', '义'],
  ['變', '变'],
  ['貓', '猫'],
  ['陳', '陈'],
  ['詞', '词'],
  ['懶', '懒'],
  ['調', '调'],
  ['書', '书'],
  ['職', '职'],
  ['蕭', '萧'],
  ['風', '风'],
  ['雲', '云'],
  ['聽', '听'],
  ['濤', '涛'],
  ['無', '无'],
  ['驚', '惊'],
  ['樂', '乐'],
]

const variantMap = new Map<string, string>(
  SIMPLIFIED_TRADITIONAL_GROUPS.flatMap(group =>
    group.map(char => [char, group[0]] as const),
  ),
)

const normalizeVariants = (value: string) =>
  Array.from(value)
    .map(char => variantMap.get(char) ?? char)
    .join('')

export const normalizeBookshelfSearch = (value?: string): string => {
  return normalizeVariants((value ?? '').trim().toLowerCase()).replace(
    SEARCH_PUNCTUATION,
    '',
  )
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
