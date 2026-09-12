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
        onProgress: (current: Int, total: Int, item: MediaItem) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val settings = getBackupSettings()
        if (!settings.isAutoBackupEnabled) return@withContext 0

        val creds = r2Repository.getCredentials()
        if (creds.secretAccessKey.isEmpty() || creds.bucketName.isEmpty()) return@withContext 0

        val allItems = mediaRepository.loadMediaItems()
        val eligible = allItems.filter { item ->
            (item.isVideo && settings.backupVideos) || (!item.isVideo && settings.backupPhotos)
        }

        val unbacked = eligible.filter { !isAlreadyBackedUp(it) }
        var successCount = 0

        for ((index, item) in unbacked.withIndex()) {
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
        val allMedia = mediaRepository.loadMediaItems().associateBy { it.id }

        for (record in failed) {
            val item = allMedia[record.mediaStoreId]
            if (item != null) {
                uploadSingleMedia(item)
            }
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    override suspend fun getBackupStats(): BackupStats = withContext(Dispatchers.IO) {
        val allRecords = dao.getAllRecords()
        val backedUp = allRecords.count { it.status == BackupStatus.COMPLETED.name }
        val pending = allRecords.count { it.status == BackupStatus.PENDING.name }
        val uploading = allRecords.count { it.status == BackupStatus.UPLOADING.name }
        val failed = allRecords.count { it.status == BackupStatus.FAILED.name }
        val settings = getBackupSettings()

        BackupStats(
            backedUpCount = backedUp,
            pendingCount = pending,
            uploadingCount = uploading,
            failedCount = failed,
            lastBackupTime = settings.lastBackupTime
        )
    }

    override fun scheduleWorker(settings: BackupSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.isAutoBackupEnabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME_PERIODIC)
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

        // Also enqueue a one-time work immediate sync
        val oneTimeRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            AutoBackupWorker.WORK_NAME_ONETIME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
