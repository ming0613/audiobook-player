package com.ming0613.audiobookplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.ming0613.audiobookplayer.data.db.AudiobookDatabase
import com.ming0613.audiobookplayer.data.db.BookEntity
import com.ming0613.audiobookplayer.data.db.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书库扫描：把"书库根目录"下的每个子文件夹识别为一本书，
 * 文件夹内的音频文件识别为章节，并读取每个音频的时长。
 *
 * 全部在 IO 线程执行，扫描结果整体写入 Room（一个事务）。
 * 重新扫描时会保留已有书籍的收听进度（按文件夹 Uri 匹配）。
 */
class LibraryRepository(private val context: Context) {

    private val dao = AudiobookDatabase.get(context).bookDao()

    private val audioExtensions = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma", "amr"
    )

    /** 扫描结果统计，用于 UI 提示 */
    data class ScanResult(val bookCount: Int, val chapterCount: Int)

    suspend fun scanLibrary(treeUriString: String): ScanResult = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUriString.toUri())
            ?: return@withContext ScanResult(0, 0)

        // 旧数据按 id 建索引，重扫时保留进度
        val existing = dao.getBooksSnapshot().associateBy { it.id }

        val books = mutableListOf<BookEntity>()
        val chapters = mutableListOf<ChapterEntity>()

        tree.listFiles()
            .filter { it.isDirectory }
            .sortedBy { it.name?.lowercase() }
            .forEach { dir ->
                val audioFiles = dir.listFiles()
                    .filter { file ->
                        file.isFile &&
                            file.name?.substringAfterLast('.', "")?.lowercase() in audioExtensions
                    }
                    .sortedBy { it.name?.lowercase() }

                if (audioFiles.isEmpty()) return@forEach

                val bookId = dir.uri.toString()
                val retriever = MediaMetadataRetriever()
                var totalDuration = 0L

                audioFiles.forEachIndexed { index, file ->
                    val duration = try {
                        retriever.setDataSource(context, file.uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                    } catch (e: Exception) {
                        0L // 单个文件损坏不阻断整体扫描
                    }
                    totalDuration += duration
                    chapters += ChapterEntity(
                        bookId = bookId,
                        index = index,
                        name = file.name ?: "第 ${index + 1} 章",
                        uri = file.uri.toString(),
                        durationMs = duration
                    )
                }
                retriever.release()

                val old = existing[bookId]
                books += BookEntity(
                    id = bookId,
                    name = dir.name ?: "未命名",
                    chapterCount = audioFiles.size,
                    totalDurationMs = totalDuration,
                    currentChapterIndex = old?.currentChapterIndex ?: 0,
                    currentPositionMs = old?.currentPositionMs ?: 0,
                    lastPlayedAt = old?.lastPlayedAt ?: 0,
                    addedAt = old?.addedAt ?: System.currentTimeMillis()
                )
            }

        dao.replaceAll(books, chapters)
        ScanResult(bookCount = books.size, chapterCount = chapters.size)
    }
}
