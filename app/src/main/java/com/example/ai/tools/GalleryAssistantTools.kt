package com.example.ai.tools

import com.example.ai.model.AiActionResult
import com.example.ai.model.CloudItemSummary
import com.example.ai.model.MediaItemSummary
import com.example.data.local.SortOrder
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item

sealed class ToolResult<out T> {
    data class Success<out T>(val data: T, val actionResult: AiActionResult? = null) : ToolResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ToolResult<Nothing>()
}

enum class DateFilterType {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH
}

/**
 * Explicit safe tool interface for CloudGallery.
 *
 * Privacy & Security Guarantees:
 * - NO raw filesystem path access
 * - NO credentials, AccessKeyId, or SecretAccessKey exposure
 * - NO transmission of raw media image/video bytes
 * - Safe abstraction over existing repositories and use cases
 */
interface GalleryAssistantTools {

    suspend fun searchLocalMedia(
        query: String? = null,
        mediaType: String? = null, // "video", "photo", "image"
        isFavorite: Boolean? = null,
        minSizeBytes: Long? = null,
        maxSizeBytes: Long? = null,
        albumName: String? = null,
        dateFilter: DateFilterType = DateFilterType.ALL
    ): ToolResult<List<MediaItem>>

    suspend fun searchCloudMedia(
        query: String? = null,
        category: MediaCategory? = null,
        prefix: String = ""
    ): ToolResult<List<R2Item>>

    suspend fun getStorageUsage(): ToolResult<AiActionResult.StorageUsageResult>

    suspend fun getBackupStatus(): ToolResult<AiActionResult.BackupStatusResult>

    suspend fun startBackup(onlyVideos: Boolean = false): ToolResult<String>

    suspend fun getUploadStatus(): ToolResult<String>

    suspend fun downloadCloudFile(key: String): ToolResult<String>

    suspend fun requestDelete(items: List<MediaItem>): ToolResult<String>

    suspend fun refreshGallery(): ToolResult<String>

    suspend fun openMedia(mediaId: Long): ToolResult<MediaItem>

    suspend fun openAlbum(albumName: String): ToolResult<String>

    suspend fun filterMedia(tabName: String, query: String? = null): ToolResult<String>

    suspend fun sortMedia(sortOrder: SortOrder): ToolResult<String>

    suspend fun getFileDetails(mediaId: Long): ToolResult<MediaItemSummary>

    suspend fun testR2Connection(): ToolResult<AiActionResult.R2ConnectionResult>

    suspend fun explainUploadFailure(): ToolResult<AiActionResult.FailureDiagnosticResult>

    suspend fun findDuplicates(): ToolResult<AiActionResult.DuplicatesResult>
}
