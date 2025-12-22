package com.example.ai_guardian_companion.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ai_guardian_companion.storage.dao.*
import com.example.ai_guardian_companion.storage.entity.*

/**
 * 对话数据库
 * 版本：1
 */
@Database(
    entities = [
        SessionEntity::class,
        TurnEntity::class,
        ImageEntity::class,
        EventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ConversationDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
    abstract fun imageDao(): ImageDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: ConversationDatabase? = null

        private const val DATABASE_NAME = "conversation_database"

        fun getDatabase(context: Context): ConversationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConversationDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // 开发阶段可用，生产环境需要 migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
