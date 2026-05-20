import type { BaseBook } from '@/book'

type ChapterLinkBook = Partial<BaseBook> & {
  durChapterIndex?: number
  durChapterPos?: number
  chapterIndex?: number
  chapterPos?: number
  isSeachBook?: boolean
  respondTime?: unknown
}

export const getChapterHref = (book: ChapterLinkBook) => {
  const query = new URLSearchParams(getChapterQuery(book))
  return `#/chapter?${query.toString()}`
}

export const getChapterQuery = (book: ChapterLinkBook) => {
  return {
    bookUrl: book.bookUrl ?? '',
    bookName: book.name ?? '',
    bookAuthor: book.author ?? '',
    chapterIndex: String(book.durChapterIndex ?? book.chapterIndex ?? 0),
    chapterPos: String(book.durChapterPos ?? book.chapterPos ?? 0),
    isSeachBook: String(book.isSeachBook ?? 'respondTime' in book),
  }
}
