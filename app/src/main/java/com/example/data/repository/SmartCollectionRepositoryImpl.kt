package com.example.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ai.classifier.SmartCollectionClassifier
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.MediaAnalysisStateEntity
import com.example.data.local.entity.SmartClassificationEntity
import com.example.domain.model.AnalysisRunState
import com.example.domain.model.AnalysisStatus
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartCollection
import com.example.domain.model.SmartCollectionCategories
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.SmartCollectionRepository
import com.example.worker.SmartCollectionAnalysisWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SmartCollectionRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val mediaRepository: MediaRepository,
    private val classifier: SmartCollectionClassifier,
    private val preferencesManager: PreferencesManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SmartCollectionRepository {

    private val dao = database.smartCollectionDao()

    private val _analysisStatusFlow = MutableStateFlow(AnalysisStatus())
    override fun getAnalysisStatusFlow(): Flow<AnalysisStatus> = _analysisStatusFlow.asStateFlow()
    override fun getCurrentAnalysisStatus(): AnalysisStatus = _analysisStatusFlow.value

    private var activeAnalysisJob: Job? = null

    override fun getSmartCollectionsFlow(): Flow<List<SmartCollection>> {
        return combine(
            dao.getCategoryCountsFlow(),
            mediaRepository.getMediaItemsFlow()
        ) { categoryCounts: List<com.example.data.local.dao.CategoryCountTuple>, allMedia: List<MediaItem> ->
            val mediaById = allMedia.associateBy { it.id }
            val countsMap = categoryCounts.associateBy { it.categoryId }

            // Build collections for categories that have items first, or show standard categories
            val categoriesWithItems = categoryCounts.mapNotNull { tuple ->
                val categoryDef = SmartCollectionCategories.findById(tuple.categoryId)
                val mediaIds = dao.getMediaStoreIdsForCategory(tuple.categoryId)
                val coverItem = mediaIds.firstNotNullOfOrNull { mediaById[it] }

                SmartCollection(
                    id = tuple.categoryId,
                    name = categoryDef?.displayName ?: tuple.categoryDisplayName,
                    description = categoryDef?.description ?: "Smart collection",
                    itemCount = tuple.itemCount,
                    coverMediaItem = coverItem,
                    coverUri = coverItem?.uriString
                )
            }

            // Also show empty standard categories if none exist yet, or keep all defined categories visible
            val existingIds = categoriesWithItems.map { it.id }.toSet()
            val remainingCategories = SmartCollectionCategories.ALL
                .filter { it.id !in existingIds }
                .map { def ->
                    SmartCollection(
                        id = def.id,
                        name = def.displayName,
                        description = def.description,
                        itemCount = 0,
                        coverMediaItem = null,
                        coverUri = null
                    )
                }

            // Return non-empty first, sorted by item count descending, then empty categories
            (categoriesWithItems.sortedByDescending { it.itemCount } + remainingCategories)
        }
    }

    override suspend fun getSmartCollections(): List<SmartCollection> = withContext(Dispatchers.IO) {
        val categoryCounts = dao.getCategoryCounts()
        val allMedia = mediaRepository.loadMediaItems()
        val mediaById = allMedia.associateBy { it.id }

        val categoriesWithItems = categoryCounts.mapNotNull { tuple ->
            val categoryDef = SmartCollectionCategories.findById(tuple.categoryId)
            val mediaIds = dao.getMediaStoreIdsForCategory(tuple.categoryId)
            val coverItem = mediaIds.firstNotNullOfOrNull { mediaById[it] }

            SmartCollection(
                id = tuple.categoryId,
                name = categoryDef?.displayName ?: tuple.categoryDisplayName,
                description = categoryDef?.description ?: "Smart collection",
                itemCount = tuple.itemCount,
                coverMediaItem = coverItem,
                coverUri = coverItem?.uriString
            )
        }

        val existingIds = categoriesWithItems.map { it.id }.toSet()
        val remainingCategories = SmartCollectionCategories.ALL
            .filter { it.id !in existingIds }
            .map { def ->
                SmartCollection(
                    id = def.id,
                    name = def.displayName,
                    description = def.description,
                    itemCount = 0,
                    coverMediaItem = null,
                    coverUri = null
                )
            }

        (categoriesWithItems.sortedByDescending { it.itemCount } + remainingCategories)
    }

    override fun getCollectionMediaFlow(categoryId: String): Flow<List<MediaItem>> {
        return combine(
            dao.getMediaStoreIdsForCategoryFlow(categoryId),
            mediaRepository.getMediaItemsFlow()
        ) { ids: List<Long>, allMedia: List<MediaItem> ->
            val mediaById = allMedia.associateBy { it.id }
            ids.mapNotNull { mediaById[it] }
        }
    }

    override suspend fun getCollectionMedia(categoryId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val ids = dao.getMediaStoreIdsForCategory(categoryId)
        val allMedia = mediaRepository.loadMediaItems()
        val mediaById = allMedia.associateBy { it.id }
        ids.mapNotNull { mediaById[it] }
    }

    override suspend fun isModelInstalled(): Boolean = classifier.isModelInstalled()

    override suspend fun installModel(onProgress: (Float) -> Unit): Result<Unit> {
        val result = classifier.installModel(onProgress)
        if (result.isSuccess) {
            preferencesManager.setSmartCollectionsModelInstalled(true)
        }
        return result
    }

    override suspend fun analyzeUnprocessedMedia(forceAll: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val isEnabled = preferencesManager.smartCollectionsEnabledFlow.first()
        if (!isEnabled) {
            return@withContext Result.failure(IllegalStateException("Smart Collections are disabled"))
        }

        if (!classifier.isModelInstalled()) {
            return@withContext Result.failure(IllegalStateException("AI model is not installed"))
        }

        activeAnalysisJob?.cancel()
        val job = coroutineScope.launch {
            runAnalysis(forceAll)
        }
        activeAnalysisJob = job
        Result.success(Unit)
    }

    private suspend fun runAnalysis(forceAll: Boolean) = withContext(Dispatchers.IO) {
        val analyzeVideos = preferencesManager.smartCollectionsAnalyzeVideosFlow.first()
        val allMedia = mediaRepository.loadMediaItems()
        val existingStates = dao.getAllAnalysisStates().associateBy { it.mediaStoreId }

        val pendingMedia = if (forceAll) {
            allMedia.filter { !it.isVideo || analyzeVideos }
        } else {
            allMedia.filter { item ->
                if (item.isVideo && !analyzeVideos) return@filter false
                val state = existingStates[item.id]
                state == null ||
                        state.status != "ANALYZED" ||
                        state.dateModified != item.dateModified ||
                        state.modelVersion != classifier.modelVersion
            }
        }

        val totalPending = pendingMedia.size
        val alreadyAnalyzed = allMedia.size - totalPending

        if (totalPending == 0) {
            _analysisStatusFlow.value = AnalysisStatus(
                state = AnalysisRunState.COMPLETED,
                totalMediaCount = allMedia.size,
                analyzedCount = allMedia.size,
                pendingCount = 0,
                progress = 1.0f,
                message = "Smart Collections are up to date"
            )
            return@withContext
        }

        _analysisStatusFlow.value = AnalysisStatus(
            state = AnalysisRunState.ANALYZING,
            totalMediaCount = allMedia.size,
            analyzedCount = alreadyAnalyzed,
            pendingCount = totalPending,
            progress = alreadyAnalyzed.toFloat() / allMedia.size.toFloat().coerceAtLeast(1f),
            message = "Starting analysis..."
        )

        var processedThisSession = 0
        var failedCount = 0

        for (item in pendingMedia) {
            if (_analysisStatusFlow.value.state == AnalysisRunState.PAUSED) {
                break
            }

            _analysisStatusFlow.value = _analysisStatusFlow.value.copy(
                currentItemName = item.name,
                message = "Analyzing ${item.name} (${processedThisSession + 1} of $totalPending)..."
            )

            try {
                val uri = android.net.Uri.parse(item.uriString)
                val result = if (item.isVideo) {
                    classifier.classifyVideo(item.id, uri, item.durationMs)
                } else {
                    classifier.classifyImage(
                        mediaId = item.id,
                        uri = uri,
                        mimeType = item.mimeType,
                        width = item.width,
                        height = item.height,
                        dateAdded = item.dateAdded
                    )
                }

                if (result.isAnalyzed && result.categories.isNotEmpty()) {
                    dao.deleteClassificationsForMedia(item.id)

                    val entities = result.categories.map { cat ->
                        val def = SmartCollectionCategories.findById(cat.categoryId)
                        SmartClassificationEntity(
                            mediaStoreId = item.id,
                            categoryId = cat.categoryId,
                            categoryDisplayName = def?.displayName ?: cat.categoryDisplayName,
                            confidence = cat.confidence,
                            analyzedAt = System.currentTimeMillis(),
                            modelVersion = classifier.modelVersion
                        )
                    }
                    dao.insertClassifications(entities)

                    dao.insertOrUpdateState(
                        MediaAnalysisStateEntity(
                            mediaStoreId = item.id,
                            status = "ANALYZED",
                            dateModified = item.dateModified,
                            lastAnalyzedAt = System.currentTimeMillis(),
                            modelVersion = classifier.modelVersion,
                            errorMessage = null
                        )
                    )
                } else {
                    if (!result.isAnalyzed) failedCount++
                    dao.insertOrUpdateState(
                        MediaAnalysisStateEntity(
                            mediaStoreId = item.id,
                            status = if (result.isAnalyzed) "ANALYZED" else "FAILED",
                            dateModified = item.dateModified,
                            lastAnalyzedAt = System.currentTimeMillis(),
                            modelVersion = classifier.modelVersion,
                            errorMessage = result.errorMessage
                        )
                    )
                }
            } catch (e: Exception) {
                failedCount++
                dao.insertOrUpdateState(
                    MediaAnalysisStateEntity(
                        mediaStoreId = item.id,
                        status = "FAILED",
                        dateModified = item.dateModified,
                        lastAnalyzedAt = System.currentTimeMillis(),
                        modelVersion = classifier.modelVersion,
                        errorMessage = e.localizedMessage
                    )
                )
            }

            processedThisSession++
            val currentAnalyzed = alreadyAnalyzed + processedThisSession
            val progress = currentAnalyzed.toFloat() / allMedia.size.toFloat().coerceAtLeast(1f)

            _analysisStatusFlow.value = _analysisStatusFlow.value.copy(
                analyzedCount = currentAnalyzed,
                pendingCount = totalPending - processedThisSession,
                failedCount = failedCount,
                progress = progress,
                message = "$currentAnalyzed of ${allMedia.size} analyzed"
            )
        }

        val finalState = if (_analysisStatusFlow.value.state == AnalysisRunState.PAUSED) {
            AnalysisRunState.PAUSED
        } else {
            AnalysisRunState.COMPLETED
        }

        _analysisStatusFlow.value = _analysisStatusFlow.value.copy(
            state = finalState,
            currentItemName = null,
            message = if (finalState == AnalysisRunState.COMPLETED) "Smart Collections are up to date" else "Analysis paused"
        )
    }

    override fun pauseAnalysis() {
        _analysisStatusFlow.value = _analysisStatusFlow.value.copy(
            state = AnalysisRunState.PAUSED,
            message = "Analysis paused"
        )
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
    }

    override suspend fun clearClassificationData(): Result<Unit> = withContext(Dispatchers.IO) {
        pauseAnalysis()
        dao.clearAllClassifications()
        dao.clearAllAnalysisStates()
        _analysisStatusFlow.value = AnalysisStatus(
            state = AnalysisRunState.IDLE,
            message = "Classification data cleared"
        )
        Result.success(Unit)
    }

    override suspend fun reanalyzeAllMedia(): Result<Unit> = withContext(Dispatchers.IO) {
        clearClassificationData()
        analyzeUnprocessedMedia(forceAll = true)
    }

    override fun scheduleWorker() {
        val workManager = WorkManager.getInstance(context)
        coroutineScope.launch {
            val isEnabled = preferencesManager.smartCollectionsEnabledFlow.first()
            if (!isEnabled) {
                workManager.cancelUniqueWork(SmartCollectionAnalysisWorker.WORK_NAME_PERIODIC)
                return@launch
            }

            val wifiOnly = preferencesManager.smartCollectionsWifiOnlyFlow.first()
            val requireCharging = preferencesManager.smartCollectionsRequireChargingFlow.first()

            val constraints = Constraints.Builder().apply {
                if (wifiOnly) {
                    setRequiredNetworkType(NetworkType.UNMETERED)
                } else {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
                if (requireCharging) {
                    setRequiresCharging(true)
                }
            }.build()

            val periodicRequest = PeriodicWorkRequestBuilder<SmartCollectionAnalysisWorker>(
                3, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                SmartCollectionAnalysisWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            // Optional one-time kickoff if on Wi-Fi/Charging
            val oneTimeRequest = OneTimeWorkRequestBuilder<SmartCollectionAnalysisWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                SmartCollectionAnalysisWorker.WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
