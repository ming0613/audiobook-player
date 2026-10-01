package com.ming0613.audiobookplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一本书 = 用户设备上的一个文件夹。
 * id 直接使用文件夹的 SAF Uri 字符串，天然全局唯一。
 * 进度字段（currentChapterIndex / currentPositionMs / lastPlayedAt）内嵌在表里，
 * MVP 阶段一本书只有一条进度记录，无需单独建表。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val chapterCount: Int,
    val totalDurationMs: Long,
    val currentChapterIndex: Int = 0,
    val currentPositionMs: Long = 0,
    /** 上次收听时间戳；0 表示从未播放 */
    val lastPlayedAt: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 一章 = 书文件夹里的一个音频文件。
 * 联合主键 (bookId, index)；书被删除时章节级联删除。
 */
@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    val bookId: String,
    val index: Int,
    val name: String,
    val uri: String,
    val durationMs: Long
)
