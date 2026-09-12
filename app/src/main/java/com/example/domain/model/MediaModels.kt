package com.example.domain.model

enum class BackupStatus {
    IDLE,
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}

data class MediaItem(
    val id: Long,
    val uriString: String,
    val name: String,
    val path: String = "",
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "image/*",
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val albumName: String = "Internal",
    val isFavorite: Boolean = false,
    val isCloud: Boolean = false,
    val cloudKey: String? = null,
    val backupStatus: BackupStatus? = null
)

data class Album(
    val id: String,
    val name: String,
    val coverUri: String,
    val itemCount: Int,
    val isVideoAlbum: Boolean = false
)

data class R2Item(
    val key: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isFolder: Boolean,
    val mimeType: String = ""
)

data class StorageStats(
    val photoCount: Int = 0,
    val photoBytes: Long = 0L,
    val videoCount: Int = 0,
    val videoBytes: Long = 0L,
    val totalCount: Int = 0,
    val totalBytes: Long = 0L,
    val cloudObjectCount: Int = 0,
    val cloudEstimatedBytes: Long = 0L
)

data class DuplicateGroup(
    val signature: String, // e.g. name + size or size
    val size: Long,
    val items: List<MediaItem>
)
