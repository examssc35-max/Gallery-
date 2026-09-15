package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BackupRecordDao
import com.example.data.local.dao.FavoriteDao
import com.example.data.local.dao.SmartCollectionDao
import com.example.data.local.dao.TrashDao
import com.example.data.local.entity.BackupRecordEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.MediaAnalysisStateEntity
import com.example.data.local.entity.SmartClassificationEntity
import com.example.data.local.entity.TrashEntity

@Database(
    entities = [
        BackupRecordEntity::class,
        FavoriteEntity::class,
        TrashEntity::class,
        SmartClassificationEntity::class,
        MediaAnalysisStateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trashDao(): TrashDao
    abstract fun smartCollectionDao(): SmartCollectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloudgallery.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
