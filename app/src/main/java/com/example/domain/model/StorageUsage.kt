package com.example.domain.model

import java.util.Locale

enum class MediaCategory {
    PHOTO,
    VIDEO,
    OTHER
}

data class StorageUsage(
    val totalBytes: Long = 0L,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val otherBytes: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val otherCount: Int = 0,
    val totalObjectCount: Int = 0,
    val lastUpdated: Long = 0L,
    val isCached: Boolean = false
) {
    val isEmpty: Boolean
        get() = totalObjectCount == 0 && totalBytes == 0L

    val photoPercentage: Float
        get() = if (totalBytes > 0L) (photoBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val videoPercentage: Float
        get() = if (totalBytes > 0L) (videoBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val otherPercentage: Float
        get() = if (totalBytes > 0L) (otherBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

object MediaClassifier {
    private val PHOTO_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif",
        "bmp", "wbmp", "tif", "tiff", "raw", "dng", "cr2", "nef", "arw"
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mov", "mkv", "webm", "avi", "3gp", "3gpp", "m4v",
        "flv", "wmv", "asf", "ts", "m2ts", "mts", "vob", "ogv"
    )

    fun classify(key: String, mimeType: String = ""): MediaCategory {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        if (lowerMime.startsWith("image/")) return MediaCategory.PHOTO
        if (lowerMime.startsWith("video/")) return MediaCategory.VIDEO

        val cleanKey = key.substringBefore('?').substringBefore('#')
        val ext = cleanKey.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            PHOTO_EXTENSIONS.contains(ext) -> MediaCategory.PHOTO
            VIDEO_EXTENSIONS.contains(ext) -> MediaCategory.VIDEO
            else -> MediaCategory.OTHER
        }
    }
}
