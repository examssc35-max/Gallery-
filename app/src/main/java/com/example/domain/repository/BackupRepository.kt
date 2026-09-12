package com.example.domain.repository

import com.example.data.local.BackupSettings
import com.example.data.local.entity.BackupRecordEntity
import com.example.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

sealed class UploadResult {
    data class Success(val remoteKey: String) : UploadResult()
    data class AlreadyBackedUp(val remoteKey: String) : UploadResult()
    data class Failure(val error: String) : UploadResult()
}

data class BackupStats(
    val backedUpCount: Int,
    val pendingCount: Int,
    val uploadingCount: Int,
    val failedCount: Int,
    val lastBackupTime: Long
)

interface BackupRepository {
    val backupRecordsFlow: Flow<List<BackupRecordEntity>>
    val backupSettingsFlow: Flow<BackupSettings>
    suspend fun getBackupSettings(): BackupSettings
    suspend fun updateBackupSettings(settings: BackupSettings)
    suspend fun isAlreadyBackedUp(item: MediaItem): Boolean
    suspend fun uploadSingleMedia(
        item: MediaItem,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): UploadResult
    suspend fun runBackupPass(onProgress: (current: Int, total: Int, item: MediaItem) -> Unit = { _, _, _ -> }): Int
    suspend fun retryFailedUploads()
    suspend fun clearHistory()
    suspend fun getBackupStats(): BackupStats
    fun scheduleWorker(settings: BackupSettings)
}
