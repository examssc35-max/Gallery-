package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BackupRecordEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.TrashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records ORDER BY lastBackupTime DESC")
    fun getAllRecordsFlow(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records")
    suspend fun getAllRecords(): List<BackupRecordEntity>

    @Query("SELECT * FROM backup_records WHERE mediaStoreId = :id LIMIT 1")
    suspend fun getRecordById(id: Long): BackupRecordEntity?

    @Query("SELECT * FROM backup_records WHERE status = :status")
    suspend fun getRecordsByStatus(status: String): List<BackupRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: BackupRecordEntity)

    @Update
    suspend fun update(record: BackupRecordEntity)

    @Query("DELETE FROM backup_records WHERE mediaStoreId = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE backup_records SET status = 'PENDING' WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM backup_records")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT mediaStoreId FROM favorites")
    fun getAllFavoriteIdsFlow(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaStoreId = :id)")
    suspend fun isFavorite(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaStoreId = :id")
    suspend fun removeFavorite(id: Long)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrashItemsFlow(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items")
    suspend fun getAllTrashItems(): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE mediaStoreId = :id")
    suspend fun deleteTrashById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearTrash()
}
