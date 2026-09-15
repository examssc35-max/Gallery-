package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ai.classifier.OnDeviceVisionClassifier
import com.example.data.local.AppDatabase
import com.example.data.local.MediaStoreDataSource
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.MediaAnalysisStateEntity
import com.example.data.local.entity.SmartClassificationEntity
import com.example.domain.model.SmartCollectionCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SmartCollectionAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val prefs = PreferencesManager(context)
            val isEnabled = prefs.smartCollectionsEnabledFlow.first()
            if (!isEnabled) {
                return@withContext Result.success()
            }

            val classifier = OnDeviceVisionClassifier(context, prefs)
            if (!classifier.isModelInstalled()) {
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(context)
            val dao = db.smartCollectionDao()
            val mediaDataSource = MediaStoreDataSource(context)

            val analyzeVideos = prefs.smartCollectionsAnalyzeVideosFlow.first()
            val allMedia = mediaDataSource.getMediaItems()
            val existingStates = dao.getAllAnalysisStates().associateBy { it.mediaStoreId }

            // Filter media needing analysis (not analyzed, or modified since last analysis)
            val pendingMedia = allMedia.filter { item ->
                if (item.isVideo && !analyzeVideos) return@filter false

                val state = existingStates[item.id]
                state == null ||
                        state.status != "ANALYZED" ||
                        state.dateModified != item.dateModified ||
                        state.modelVersion != classifier.modelVersion
            }

            val totalCount = pendingMedia.size
            if (totalCount == 0) {
                setProgress(workDataOf("progress" to 1.0f, "status" to "Up to date"))
                return@withContext Result.success()
            }

            var processed = 0
            for (item in pendingMedia) {
                if (isStopped) {
                    return@withContext Result.retry()
                }

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
                    // Remove old classifications for this item if re-analyzing
                    dao.deleteClassificationsForMedia(item.id)

                    val entities = result.categories.map { cat ->
                        val categoryDef = SmartCollectionCategories.findById(cat.categoryId)
                        SmartClassificationEntity(
                            mediaStoreId = item.id,
                            categoryId = cat.categoryId,
                            categoryDisplayName = categoryDef?.displayName ?: cat.categoryDisplayName,
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

                processed++
                val progress = processed.toFloat() / totalCount.toFloat()
                setProgress(
                    workDataOf(
                        "progress" to progress,
                        "processed" to processed,
                        "total" to totalCount,
                        "current" to item.name
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "cloudgallery_smart_collections_periodic"
        const val WORK_NAME_ONETIME = "cloudgallery_smart_collections_onetime"
    }
}
