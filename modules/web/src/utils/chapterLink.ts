import type { BaseBook } from '@/book'
import type { LocationQueryRaw, LocationQueryValueRaw } from 'vue-router'

type ChapterLinkBook = Partial<BaseBook> & {
  durChapterIndex?: number
  durChapterPos?: number
  chapterIndex?: number
  chapterPos?: number
  isSeachBook?: boolean
  respondTime?: unknown
}

const CHAPTER_QUERY_KEYS = new Set([
  'bookUrl',
  'bookName',
  'bookAuthor',
  'chapterIndex',
  'chapterPos',
  'isSeachBook',
])

const toQueryValue = (value: unknown): LocationQueryValueRaw | undefined => {
  if (value === undefined) return undefined
  if (value === null) return null
  return String(value)
}

const getWindowQuery = (): LocationQueryRaw => {
  if (typeof window === 'undefined') return {}
  return Object.fromEntries(new URLSearchParams(window.location.search).entries())
}

export const getPreservedRouteQuery = (
  query: Record<string, unknown> = {},
): LocationQueryRaw => {
  const preserved: LocationQueryRaw = {}
  for (const [key, value] of Object.entries(query)) {
    if (CHAPTER_QUERY_KEYS.has(key)) continue
    if (Array.isArray(value)) {
      const values = value
        .map(toQueryValue)
        .filter((item): item is LocationQueryValueRaw => item !== undefined)
      if (values.length > 0) preserved[key] = values
      continue
    }
    const nextValue = toQueryValue(value)
    if (nextValue !== undefined) preserved[key] = nextValue
  }
  return preserved
}

const toSearchParams = (query: LocationQueryRaw) => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null) params.append(key, String(item))
      }
      continue
    }
    if (value !== undefined && value !== null) params.set(key, String(value))
  }
  return params
}

export const getChapterHref = (
  book: ChapterLinkBook,
  preservedQuery: Record<string, unknown> = getWindowQuery(),
) => {
  const query = toSearchParams(getChapterQuery(book, preservedQuery))
  return `/chapter?${query.toString()}`
}

export const getChapterQuery = (
  book: ChapterLinkBook,
  preservedQuery: Record<string, unknown> = getWindowQuery(),
): LocationQueryRaw => {
  return {
    ...getPreservedRouteQuery(preservedQuery),
    bookUrl: book.bookUrl ?? '',
    bookName: book.name ?? '',
    bookAuthor: book.author ?? '',
    chapterIndex: String(book.durChapterIndex ?? book.chapterIndex ?? 0),
    chapterPos: String(book.durChapterPos ?? book.chapterPos ?? 0),
    isSeachBook: String(book.isSeachBook ?? 'respondTime' in book),
  }
}
