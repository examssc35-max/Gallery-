package com.example.domain.repository

import com.example.domain.model.AnalysisStatus
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartCollection
import kotlinx.coroutines.flow.Flow

interface SmartCollectionRepository {

    fun getSmartCollectionsFlow(): Flow<List<SmartCollection>>

    suspend fun getSmartCollections(): List<SmartCollection>

    fun getCollectionMediaFlow(categoryId: String): Flow<List<MediaItem>>

    suspend fun getCollectionMedia(categoryId: String): List<MediaItem>

    fun getAnalysisStatusFlow(): Flow<AnalysisStatus>

    fun getCurrentAnalysisStatus(): AnalysisStatus

    suspend fun isModelInstalled(): Boolean

    suspend fun installModel(onProgress: (Float) -> Unit): Result<Unit>

    suspend fun analyzeUnprocessedMedia(forceAll: Boolean = false): Result<Unit>

    fun pauseAnalysis()

    suspend fun clearClassificationData(): Result<Unit>

    suspend fun reanalyzeAllMedia(): Result<Unit>

    fun scheduleWorker()
}
