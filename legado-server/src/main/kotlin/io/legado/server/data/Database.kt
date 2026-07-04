package io.legado.server.data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * SQLite database configuration using Exposed ORM
 */
object Database {
    private var database: org.jetbrains.exposed.sql.Database? = null

    fun init(dbPath: String = "./data/legado.db") {
        database = org.jetbrains.exposed.sql.Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(Books, Chapters, ReadProgress)
        }
    }

    // Books table
    object Books : Table("books") {
        val bookUrl = varchar("book_url", 512)
        val name = varchar("name", 256)
        val author = varchar("author", 128).default("")
        val kind = varchar("kind", 128).nullable()
        val coverUrl = varchar("cover_url", 512).nullable()
        val intro = text("intro").nullable()
        val charset = varchar("charset", 32).nullable()
        val type = integer("type").default(4)
        val origin = varchar("origin", 64).default("local")
        val originName = varchar("origin_name", 256).default("")
        val totalChapterNum = integer("total_chapter_num").default(0)
        val latestChapterTitle = varchar("latest_chapter_title", 256).nullable()
        val lastCheckTime = long("last_check_time").default(0)
        val order = integer("sort_order").default(0)
        val group = long("book_group").default(0)
        val tocUrl = varchar("toc_url", 512).default("")

        override val primaryKey = PrimaryKey(bookUrl)
    }

    // Chapters table
    object Chapters : Table("chapters") {
        val url = varchar("url", 512)
        val title = varchar("title", 256)
        val bookUrl = varchar("book_url", 512).references(Books.bookUrl, onDelete = ReferenceOption.CASCADE)
        val index = integer("chapter_index")
        val isVolume = bool("is_volume").default(false)
        val start = long("start_pos").nullable()
        val end = long("end_pos").nullable()
        val startFragmentId = varchar("start_fragment_id", 256).nullable()
        val endFragmentId = varchar("end_fragment_id", 256).nullable()

        override val primaryKey = PrimaryKey(bookUrl, index)

        init {
            index("idx_chapters_book", false, bookUrl)
        }
    }

    // Reading progress table
    object ReadProgress : Table("read_progress") {
        val bookUrl = varchar("book_url", 512).references(Books.bookUrl, onDelete = ReferenceOption.CASCADE)
        val durChapterIndex = integer("dur_chapter_index").default(0)
        val durChapterPos = integer("dur_chapter_pos").default(0)
        val durChapterTime = long("dur_chapter_time").default(0)
        val durChapterTitle = varchar("dur_chapter_title", 256).nullable()

        override val primaryKey = PrimaryKey(bookUrl)
    }
}
