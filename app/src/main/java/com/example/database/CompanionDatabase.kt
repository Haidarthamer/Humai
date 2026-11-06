package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.core.model.FileType
import com.example.core.model.IngestionStatus
import com.example.core.model.MemoryTier
import com.example.database.dao.BookmarkDao
import com.example.database.dao.ConversationDao
import com.example.database.dao.DocumentDao
import com.example.database.dao.KnowledgeDao
import com.example.database.dao.MemoryDao
import com.example.database.dao.MessageDao
import com.example.database.dao.PersonalityDao
import com.example.database.entities.BookmarkEntity
import com.example.database.entities.ConversationEntity
import com.example.database.entities.DocumentEntity
import com.example.database.entities.KnowledgeChunkEntity
import com.example.database.entities.MemoryEntity
import com.example.database.entities.MessageEntity
import com.example.database.entities.PersonalityProfileEntity

class DatabaseConverters {
    @TypeConverter
    fun fromFileType(value: FileType): String = value.name

    @TypeConverter
    fun toFileType(value: String): FileType = runCatching { FileType.valueOf(value) }.getOrDefault(FileType.TXT)

    @TypeConverter
    fun fromIngestionStatus(value: IngestionStatus): String = value.name

    @TypeConverter
    fun toIngestionStatus(value: String): IngestionStatus =
        runCatching { IngestionStatus.valueOf(value) }.getOrDefault(IngestionStatus.PENDING)

    @TypeConverter
    fun fromMemoryTier(value: MemoryTier): String = value.name

    @TypeConverter
    fun toMemoryTier(value: String): MemoryTier =
        runCatching { MemoryTier.valueOf(value) }.getOrDefault(MemoryTier.WORKING)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        DocumentEntity::class,
        KnowledgeChunkEntity::class,
        MemoryEntity::class,
        PersonalityProfileEntity::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class CompanionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun documentDao(): DocumentDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun memoryDao(): MemoryDao
    abstract fun personalityDao(): PersonalityDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        fun getInstance(context: Context): CompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CompanionDatabase::class.java,
                    "companion_ai_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
