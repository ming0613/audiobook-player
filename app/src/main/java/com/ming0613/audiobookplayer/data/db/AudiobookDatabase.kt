package com.ming0613.audiobookplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AudiobookDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AudiobookDatabase? = null

        /** 单例：整个 App 共用一个数据库连接 */
        fun get(context: Context): AudiobookDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudiobookDatabase::class.java,
                    "audiobook.db"
                ).build().also { INSTANCE = it }
            }
    }
}
