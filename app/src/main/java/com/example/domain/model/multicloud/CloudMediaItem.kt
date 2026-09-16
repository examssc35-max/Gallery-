package com.example.domain.model.multicloud

import com.example.data.local.SortOrder
import com.example.domain.model.BackupStatus
import com.example.domain.model.MediaItem

data class CloudMediaItem(
    val providerId: String,
    val providerName: String,
    val remoteId: String,
    val name: String,
    val mimeType: String = "image/*",
    val size: Long = 0L,
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val thumbnailUrl: String? = null,
    val downloadUrl: String? = null,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isFolder: Boolean = false,
    val folderPath: String? = null,
    val capabilities: CloudCapabilities = CloudCapabilities()
) {
    fun toMediaItem(): MediaItem = MediaItem(
        id = ((providerId.hashCode().toLong() and 0xFFFF_FFFFL) * 31L + (remoteId.hashCode().toLong() and 0xFFFF_FFFFL))
            .let { if (it == 0L) 1L else it },
        uriString = downloadUrl ?: thumbnailUrl ?: "",
        name = name,
        path = folderPath ?: "",
        size = size,
        dateAdded = createdAt,
        dateModified = modifiedAt,
        mimeType = mimeType,
        isVideo = isVideo,
        durationMs = durationMs,
        width = width,
        height = height,
        albumName = providerName,
        isCloud = true,
        cloudKey = remoteId,
        backupStatus = BackupStatus.COMPLETED
    )
}

data class MultiCloudMediaPage(
    val items: List<CloudMediaItem>,
    val folders: List<String> = emptyList(),
    val nextContinuationToken: String? = null,
    val isTruncated: Boolean = false
)

data class CloudSearchRequest(
    val providerId: String? = null,
    val query: String? = null,
    val mediaType: String? = null, // "photo", "video", or null
    val sort: SortOrder = SortOrder.DATE_DESC,
    val minSizeBytes: Long? = null
)

data class CloudStorageQuota(
    val providerId: String,
    val providerName: String,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val isAvailable: Boolean = true,
    val statusMessage: String? = null
)

data class ProviderConnectionInfo(
    val providerId: String,
    val displayName: String,
    val connectionState: CloudConnectionState,
    val capabilities: CloudCapabilities,
    val storageQuota: CloudStorageQuota? = null
)
