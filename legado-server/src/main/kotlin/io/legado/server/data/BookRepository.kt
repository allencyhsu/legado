package io.legado.server.data

import io.legado.server.model.Book
import io.legado.server.model.BookChapter
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Data access layer for books and chapters
 */
object BookRepository {

    fun getAllBooks(): List<Book> = transaction {
        Database.Books.selectAll()
            .orderBy(Database.Books.lastCheckTime, SortOrder.DESC)
            .map { it.toBook() }
    }

    fun getBook(bookUrl: String): Book? = transaction {
        Database.Books.selectAll()
            .where { Database.Books.bookUrl eq bookUrl }
            .map { it.toBook() }
            .singleOrNull()
    }

    fun upsertBook(book: Book): Unit = transaction {
        val existing = Database.Books.selectAll()
            .where { Database.Books.bookUrl eq book.bookUrl }
            .count() > 0

        if (existing) {
            Database.Books.update({ Database.Books.bookUrl eq book.bookUrl }) {
                it[name] = book.name
                it[author] = book.author
                it[kind] = book.kind
                it[coverUrl] = book.coverUrl
                it[intro] = book.intro
                it[charset] = book.charset
                it[type] = book.type
                it[origin] = book.origin
                it[originName] = book.originName
                it[totalChapterNum] = book.totalChapterNum
                it[latestChapterTitle] = book.latestChapterTitle
                it[lastCheckTime] = book.lastCheckTime
                it[order] = book.order
                it[group] = book.group
                it[tocUrl] = book.tocUrl
            }
        } else {
            Database.Books.insert {
                it[bookUrl] = book.bookUrl
                it[name] = book.name
                it[author] = book.author
                it[kind] = book.kind
                it[coverUrl] = book.coverUrl
                it[intro] = book.intro
                it[charset] = book.charset
                it[type] = book.type
                it[origin] = book.origin
                it[originName] = book.originName
                it[totalChapterNum] = book.totalChapterNum
                it[latestChapterTitle] = book.latestChapterTitle
                it[lastCheckTime] = book.lastCheckTime
                it[order] = book.order
                it[group] = book.group
                it[tocUrl] = book.tocUrl
            }
        }
    }

    fun deleteBook(bookUrl: String): Unit = transaction {
        deleteBookRows(bookUrl)
    }

    fun deleteBooks(bookUrls: Set<String>): Int = transaction {
        bookUrls.forEach { deleteBookRows(it) }
        bookUrls.size
    }

    fun getChapters(bookUrl: String): List<BookChapter> = transaction {
        Database.Chapters.selectAll()
            .where { Database.Chapters.bookUrl eq bookUrl }
            .orderBy(Database.Chapters.index)
            .map { it.toChapter() }
    }

    fun saveChapters(bookUrl: String, chapters: List<BookChapter>): Unit = transaction {
        // Delete existing chapters
        Database.Chapters.deleteWhere { Database.Chapters.bookUrl eq bookUrl }

        // Insert new chapters
        Database.Chapters.batchInsert(chapters) { chapter ->
            this[Database.Chapters.url] = chapter.url
            this[Database.Chapters.title] = chapter.title
            this[Database.Chapters.bookUrl] = chapter.bookUrl
            this[Database.Chapters.index] = chapter.index
            this[Database.Chapters.isVolume] = chapter.isVolume
            this[Database.Chapters.start] = chapter.start
            this[Database.Chapters.end] = chapter.end
            this[Database.Chapters.startFragmentId] = chapter.startFragmentId
            this[Database.Chapters.endFragmentId] = chapter.endFragmentId
        }
    }

    fun getReadProgress(bookUrl: String): ReadProgressData? = transaction {
        Database.ReadProgress.selectAll()
            .where { Database.ReadProgress.bookUrl eq bookUrl }
            .map {
                ReadProgressData(
                    bookUrl = it[Database.ReadProgress.bookUrl],
                    durChapterIndex = it[Database.ReadProgress.durChapterIndex],
                    durChapterPos = it[Database.ReadProgress.durChapterPos],
                    durChapterTime = it[Database.ReadProgress.durChapterTime],
                    durChapterTitle = it[Database.ReadProgress.durChapterTitle]
                )
            }
            .singleOrNull()
    }

    fun saveReadProgress(progress: ReadProgressData): Unit = transaction {
        val existing = Database.ReadProgress.selectAll()
            .where { Database.ReadProgress.bookUrl eq progress.bookUrl }
            .count() > 0

        if (existing) {
            Database.ReadProgress.update({ Database.ReadProgress.bookUrl eq progress.bookUrl }) {
                it[durChapterIndex] = progress.durChapterIndex
                it[durChapterPos] = progress.durChapterPos
                it[durChapterTime] = progress.durChapterTime
                it[durChapterTitle] = progress.durChapterTitle
            }
        } else {
            Database.ReadProgress.insert {
                it[bookUrl] = progress.bookUrl
                it[durChapterIndex] = progress.durChapterIndex
                it[durChapterPos] = progress.durChapterPos
                it[durChapterTime] = progress.durChapterTime
                it[durChapterTitle] = progress.durChapterTitle
            }
        }
    }

    fun getReadingHistory(): List<Book> = transaction {
        Database.Books.innerJoin(Database.ReadProgress)
            .selectAll()
            .orderBy(Database.ReadProgress.durChapterTime, SortOrder.DESC)
            .map { it.toBook() }
    }

    fun deleteReadProgress(bookUrl: String): Int = transaction {
        Database.ReadProgress.deleteWhere { Database.ReadProgress.bookUrl eq bookUrl }
    }

    fun clearReadProgress(): Int = transaction {
        Database.ReadProgress.deleteAll()
    }

    private fun ResultRow.toBook(): Book {
        val progress = getReadProgress(this[Database.Books.bookUrl])
        return Book(
            bookUrl = this[Database.Books.bookUrl],
            name = this[Database.Books.name],
            author = this[Database.Books.author],
            kind = this[Database.Books.kind],
            coverUrl = this[Database.Books.coverUrl],
            intro = this[Database.Books.intro],
            charset = this[Database.Books.charset],
            type = this[Database.Books.type],
            origin = this[Database.Books.origin],
            originName = this[Database.Books.originName],
            totalChapterNum = this[Database.Books.totalChapterNum],
            latestChapterTitle = this[Database.Books.latestChapterTitle],
            lastCheckTime = this[Database.Books.lastCheckTime],
            order = this[Database.Books.order],
            group = this[Database.Books.group],
            tocUrl = this[Database.Books.tocUrl],
            durChapterIndex = progress?.durChapterIndex ?: 0,
            durChapterPos = progress?.durChapterPos ?: 0,
            durChapterTime = progress?.durChapterTime ?: 0,
            durChapterTitle = progress?.durChapterTitle
        )
    }

    private fun ResultRow.toChapter() = BookChapter(
        url = this[Database.Chapters.url],
        title = this[Database.Chapters.title],
        bookUrl = this[Database.Chapters.bookUrl],
        index = this[Database.Chapters.index],
        isVolume = this[Database.Chapters.isVolume],
        start = this[Database.Chapters.start],
        end = this[Database.Chapters.end],
        startFragmentId = this[Database.Chapters.startFragmentId],
        endFragmentId = this[Database.Chapters.endFragmentId]
    )

    private fun deleteBookRows(bookUrl: String) {
        Database.Chapters.deleteWhere { Database.Chapters.bookUrl eq bookUrl }
        Database.ReadProgress.deleteWhere { Database.ReadProgress.bookUrl eq bookUrl }
        Database.Books.deleteWhere { Database.Books.bookUrl eq bookUrl }
    }
}

data class ReadProgressData(
    val bookUrl: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTime: Long,
    val durChapterTitle: String?
)
