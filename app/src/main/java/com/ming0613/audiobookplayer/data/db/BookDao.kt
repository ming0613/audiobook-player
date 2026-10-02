package com.ming0613.audiobookplayer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** 书架排序：听过的按最近收听倒序，未听过的排在最后按名称排序（US-C2） */
    @Query(
        """
        SELECT * FROM books
        ORDER BY CASE WHEN lastPlayedAt = 0 THEN 1 ELSE 0 END,
                 lastPlayedAt DESC,
                 name COLLATE NOCASE ASC
        """
    )
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getBooksSnapshot(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    /** 保存收听进度（US-C1）：当前章节 + 章内位置 + 上次收听时间 */
    @Query(
        """
        UPDATE books
        SET currentChapterIndex = :chapterIndex,
            currentPositionMs = :positionMs,
            lastPlayedAt = :lastPlayedAt
        WHERE id = :bookId
        """
    )
    suspend fun updateProgress(
        bookId: String,
        chapterIndex: Int,
        positionMs: Long,
        lastPlayedAt: Long
    )

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    /** 当前章节之前所有章节的时长之和，用于计算完成百分比（US-A3） */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM chapters WHERE bookId = :bookId AND `index` < :beforeIndex")
    suspend fun listenedMsBefore(bookId: String, beforeIndex: Int): Long

    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId NOT IN (:keepBookIds)")
    suspend fun deleteStaleChapters(keepBookIds: List<String>)

    @Query("DELETE FROM books WHERE id NOT IN (:keepBookIds)")
    suspend fun deleteStaleBooks(keepBookIds: List<String>)

    @Query("DELETE FROM chapters")
    suspend fun clearChapters()

    @Query("DELETE FROM books")
    suspend fun clearBooks()

    /** 全量重扫：先清再写，一个事务保证不会留下中间状态 */
    @Transaction
    suspend fun replaceAll(books: List<BookEntity>, chapters: List<ChapterEntity>) {
        clearChapters()
        clearBooks()
        upsertBooks(books)
        upsertChapters(chapters)
    }
}
