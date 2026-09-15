package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.MediaAnalysisStateEntity
import com.example.data.local.entity.SmartClassificationEntity
import kotlinx.coroutines.flow.Flow

data class CategoryCountTuple(
    val categoryId: String,
    val categoryDisplayName: String,
    val itemCount: Int
)

@Dao
interface SmartCollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassifications(items: List<SmartClassificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: MediaAnalysisStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStates(states: List<MediaAnalysisStateEntity>)

    @Query("SELECT * FROM smart_classifications WHERE mediaStoreId = :mediaId")
    suspend fun getClassificationsForMedia(mediaId: Long): List<SmartClassificationEntity>

    @Query("SELECT mediaStoreId FROM smart_classifications WHERE categoryId = :categoryId ORDER BY confidence DESC, analyzedAt DESC")
    fun getMediaStoreIdsForCategoryFlow(categoryId: String): Flow<List<Long>>

    @Query("SELECT mediaStoreId FROM smart_classifications WHERE categoryId = :categoryId ORDER BY confidence DESC, analyzedAt DESC")
    suspend fun getMediaStoreIdsForCategory(categoryId: String): List<Long>

    @Query("SELECT categoryId, categoryDisplayName, COUNT(mediaStoreId) as itemCount FROM smart_classifications GROUP BY categoryId ORDER BY itemCount DESC")
    fun getCategoryCountsFlow(): Flow<List<CategoryCountTuple>>

    @Query("SELECT categoryId, categoryDisplayName, COUNT(mediaStoreId) as itemCount FROM smart_classifications GROUP BY categoryId ORDER BY itemCount DESC")
    suspend fun getCategoryCounts(): List<CategoryCountTuple>

    @Query("SELECT * FROM media_analysis_states WHERE mediaStoreId = :mediaId LIMIT 1")
    suspend fun getAnalysisState(mediaId: Long): MediaAnalysisStateEntity?

    @Query("SELECT * FROM media_analysis_states")
    suspend fun getAllAnalysisStates(): List<MediaAnalysisStateEntity>

    @Query("SELECT * FROM media_analysis_states")
    fun getAllAnalysisStatesFlow(): Flow<List<MediaAnalysisStateEntity>>

    @Query("SELECT COUNT(*) FROM media_analysis_states WHERE status = 'ANALYZED'")
    fun getAnalyzedCountFlow(): Flow<Int>

    @Query("DELETE FROM smart_classifications WHERE mediaStoreId = :mediaId")
    suspend fun deleteClassificationsForMedia(mediaId: Long)

    @Query("DELETE FROM smart_classifications")
    suspend fun clearAllClassifications()

    @Query("DELETE FROM media_analysis_states")
    suspend fun clearAllAnalysisStates()
}
