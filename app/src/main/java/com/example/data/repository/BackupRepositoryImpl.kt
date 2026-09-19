package com.example.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.BackupSettings
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.BackupRecordEntity
import com.example.domain.model.BackupStatus
import com.example.domain.model.MediaItem
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.BackupStats
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.R2Repository
import com.example.domain.repository.UploadResult
import com.example.worker.AutoBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class BackupRepositoryImpl(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val r2Repository: R2Repository,
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) : BackupRepository {

    private val dao get() = database.backupRecordDao()

    override val backupRecordsFlow: Flow<List<BackupRecordEntity>> = dao.getAllRecordsFlow()
    override val backupSettingsFlow: Flow<BackupSettings> = preferencesManager.backupSettingsFlow

    override suspend fun getBackupSettings(): BackupSettings {
        return preferencesManager.backupSettingsFlow.first()
    }

    override suspend fun updateBackupSettings(settings: BackupSettings) {
        preferencesManager.updateBackupSettings(settings)
        scheduleWorker(settings)
    }

    override suspend fun isAlreadyBackedUp(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val record = dao.getRecordById(item.id) ?: return@withContext false
        // Item is backed up if completed and size & modification timestamp match
        record.status == BackupStatus.COMPLETED.name &&
                record.fileSize == item.size &&
                record.dateModified == item.dateModified
    }

    override suspend fun uploadSingleMedia(
        item: MediaItem,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        val existing = dao.getRecordById(item.id)
        if (existing != null && existing.status == BackupStatus.COMPLETED.name &&
            existing.fileSize == item.size && existing.dateModified == item.dateModified
        ) {
            return@withContext UploadResult.AlreadyBackedUp(existing.remoteKey)
        }

        val folder = if (item.isVideo) "videos/" else "photos/"
        val remoteKey = "$folder${item.name}"

        // Mark as UPLOADING in DB
        dao.insertOrUpdate(
            BackupRecordEntity(
                mediaStoreId = item.id,
                uriString = item.uriString,
                fileName = item.name,
                fileSize = item.size,
                dateModified = item.dateModified,
                remoteKey = remoteKey,
                status = BackupStatus.UPLOADING.name,
                errorMessage = null,
                lastBackupTime = System.currentTimeMillis()
            )
        )

        val uploadResult = r2Repository.uploadMedia(item, onProgress)

        if (uploadResult.isSuccess) {
            val key = uploadResult.getOrThrow()
            dao.insertOrUpdate(
                BackupRecordEntity(
                    mediaStoreId = item.id,
                    uriString = item.uriString,
                    fileName = item.name,
                    fileSize = item.size,
                    dateModified = item.dateModified,
                    remoteKey = key,
                    status = BackupStatus.COMPLETED.name,
                    errorMessage = null,
                    lastBackupTime = System.currentTimeMillis()
                )
            )
            UploadResult.Success(key)
        } else {
            val error = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
            dao.insertOrUpdate(
                BackupRecordEntity(
                    mediaStoreId = item.id,
                    uriString = item.uriString,
                    fileName = item.name,
                    fileSize = item.size,
                    dateModified = item.dateModified,
                    remoteKey = remoteKey,
                    status = BackupStatus.FAILED.name,
                    errorMessage = error,
                    lastBackupTime = System.currentTimeMillis()
                )
            )
            UploadResult.Failure(error)
        }
    }

    override suspend fun runBackupPass(
        force: Boolean,
        onProgress: (current: Int, total: Int, item: MediaItem) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val settings = getBackupSettings()
        if (!settings.isAutoBackupEnabled && !force) return@withContext 0

        val creds = r2Repository.getCredentials()
        val isConnected = creds.isVerified || (creds.secretAccessKey.isNotBlank() && creds.bucketName.isNotBlank() && creds.accountId.isNotBlank())
        if (!isConnected) {
            if (force) {
                throw IllegalStateException("Cloudflare R2 is not connected")
            }
            return@withContext 0
        }

        val allItems = try {
            mediaRepository.loadMediaItems()
        } catch (e: Exception) {
            emptyList()
        }

        val eligible = allItems.filter { item ->
            (item.isVideo && settings.backupVideos) || (!item.isVideo && settings.backupPhotos)
        }

        val unbacked = eligible.filter { !isAlreadyBackedUp(it) }
        var successCount = 0

        for ((index, item) in unbacked.withIndex()) {
            if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive!!) {
                break
            }
            onProgress(index + 1, unbacked.size, item)
            val result = uploadSingleMedia(item)
            if (result is UploadResult.Success || result is UploadResult.AlreadyBackedUp) {
                successCount++
            }
        }

        if (successCount > 0) {
            preferencesManager.updateBackupSettings(
                settings.copy(lastBackupTime = System.currentTimeMillis())
            )
        }

        successCount
    }

    override suspend fun retryFailedUploads() = withContext(Dispatchers.IO) {
        val failed = dao.getRecordsByStatus(BackupStatus.FAILED.name)
        val allMedia = try {
            mediaRepository.loadMediaItems().associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        var anySuccess = false
        for (record in failed) {
            val item = allMedia[record.mediaStoreId]
            if (item != null) {
                val res = uploadSingleMedia(item)
                if (res is UploadResult.Success || res is UploadResult.AlreadyBackedUp) {
                    anySuccess = true
                }
            } else {
                // Stale record: local media was deleted
                dao.deleteById(record.mediaStoreId)
            }
        }

        if (anySuccess) {
            val settings = getBackupSettings()
            preferencesManager.updateBackupSettings(
                settings.copy(lastBackupTime = System.currentTimeMillis())
            )
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    override suspend fun getBackupStats(): BackupStats = withContext(Dispatchers.IO) {
        val allRecords = dao.getAllRecords()
        val completedRecords = allRecords.filter { it.status == BackupStatus.COMPLETED.name }
        val failedRecords = allRecords.filter { it.status == BackupStatus.FAILED.name }
        val uploadingRecords = allRecords.filter { it.status == BackupStatus.UPLOADING.name }
        val settings = getBackupSettings()

        val allMedia = try {
            mediaRepository.loadMediaItems()
        } catch (e: Exception) {
            emptyList()
        }

        val completedIds = completedRecords.associateBy { it.mediaStoreId }
        val eligible = allMedia.filter { item ->
            (item.isVideo && settings.backupVideos) || (!item.isVideo && settings.backupPhotos)
        }

        val pendingCount = eligible.count { item ->
            val record = completedIds[item.id]
            record == null || record.fileSize != item.size || record.dateModified != item.dateModified
        }

        BackupStats(
            backedUpCount = completedRecords.size,
            pendingCount = pendingCount,
            uploadingCount = uploadingRecords.size,
            failedCount = failedRecords.size,
            lastBackupTime = settings.lastBackupTime
        )
    }

    override fun scheduleWorker(settings: BackupSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.isAutoBackupEnabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME_ONETIME)
            return
        }

        val constraints = Constraints.Builder().apply {
            if (settings.wifiOnly) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            if (settings.requireCharging) {
                setRequiresCharging(true)
            }
        }.build()

        val periodicRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            2, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}
