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
    val mimeType: String = "",
    val downloadUrl: String? = null
) {
    val fileType: CloudFileType
        get() = if (isFolder) CloudFileType.FOLDER else CloudFileTypeResolver.resolve(mimeType, name)

    val isMedia: Boolean
        get() = !isFolder && (fileType == CloudFileType.IMAGE || fileType == CloudFileType.VIDEO)

    fun toMediaItem(): MediaItem = MediaItem(
        id = (key.hashCode().toLong() and 0x7FFFFFFFL) + 2_000_000_000L,
        uriString = downloadUrl ?: "",
        name = name,
        path = key,
        size = size,
        dateAdded = if (lastModified > 0) lastModified / 1000 else System.currentTimeMillis() / 1000,
        dateModified = if (lastModified > 0) lastModified else System.currentTimeMillis(),
        mimeType = if (mimeType.isNotEmpty()) mimeType else if (fileType == CloudFileType.VIDEO) "video/mp4" else "image/jpeg",
        isVideo = fileType == CloudFileType.VIDEO || mimeType.startsWith("video/"),
        albumName = "Cloudflare R2",
        isFavorite = false,
        isCloud = true,
        cloudKey = key,
        backupStatus = BackupStatus.COMPLETED
    )
}

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
