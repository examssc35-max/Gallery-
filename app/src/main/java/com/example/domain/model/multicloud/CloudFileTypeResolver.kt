package com.example.domain.model.multicloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class CloudFileType(val displayName: String, val categoryName: String) {
    IMAGE("Photos", "Photos"),
    VIDEO("Videos", "Videos"),
    AUDIO("Audio", "Audio"),
    DOCUMENT("Documents", "Documents"),
    ARCHIVE("Archives", "Archives"),
    APK("APKs", "APKs"),
    TEXT("Text", "Text"),
    OTHER("Other", "Other")
}

object CloudFileTypeResolver {

    private val PHOTO_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif",
        "bmp", "wbmp", "tif", "tiff", "raw", "dng", "cr2", "nef", "arw", "svg"
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mov", "mkv", "webm", "avi", "3gp", "3gpp", "m4v",
        "flv", "wmv", "asf", "ts", "m2ts", "mts", "vob", "ogv"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "wma",
        "mid", "midi", "aiff", "alac", "amr"
    )

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "odp", "rtf", "pages", "numbers", "key"
    )

    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "zst", "iso"
    )

    private val APK_EXTENSIONS = setOf(
        "apk", "xapk", "apks", "aab"
    )

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "log", "json", "xml", "csv", "tsv",
        "yaml", "yml", "html", "htm", "css", "js", "ts", "kt", "java",
        "c", "cpp", "h", "hpp", "py", "sh", "sql", "ini", "conf", "properties"
    )

    /**
     * Resolves the CloudFileType category prioritizing the provider's MIME type,
     * falling back to the filename extension when necessary.
     */
    fun resolve(mimeType: String?, fileNameOrPath: String): CloudFileType {
        val lowerMime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()

        if (lowerMime.isNotEmpty() && lowerMime != "application/octet-stream") {
            when {
                lowerMime.startsWith("image/") -> return CloudFileType.IMAGE
                lowerMime.startsWith("video/") -> return CloudFileType.VIDEO
                lowerMime.startsWith("audio/") -> return CloudFileType.AUDIO
                lowerMime == "application/pdf" ||
                        lowerMime.contains("officedocument") ||
                        lowerMime.contains("msword") ||
                        lowerMime.contains("ms-excel") ||
                        lowerMime.contains("ms-powerpoint") ||
                        lowerMime.contains("opendocument") -> return CloudFileType.DOCUMENT
                lowerMime.contains("zip") ||
                        lowerMime.contains("compressed") ||
                        lowerMime.contains("tar") ||
                        lowerMime.contains("archive") ||
                        lowerMime.contains("gzip") -> return CloudFileType.ARCHIVE
                lowerMime.contains("android.package-archive") -> return CloudFileType.APK
                lowerMime.startsWith("text/") ||
                        lowerMime.contains("json") ||
                        lowerMime.contains("xml") ||
                        lowerMime.contains("csv") -> return CloudFileType.TEXT
            }
        }

        val cleanName = fileNameOrPath.substringBefore('?').substringBefore('#')
        val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            PHOTO_EXTENSIONS.contains(ext) -> CloudFileType.IMAGE
            VIDEO_EXTENSIONS.contains(ext) -> CloudFileType.VIDEO
            AUDIO_EXTENSIONS.contains(ext) -> CloudFileType.AUDIO
            DOCUMENT_EXTENSIONS.contains(ext) -> CloudFileType.DOCUMENT
            ARCHIVE_EXTENSIONS.contains(ext) -> CloudFileType.ARCHIVE
            APK_EXTENSIONS.contains(ext) -> CloudFileType.APK
            TEXT_EXTENSIONS.contains(ext) -> CloudFileType.TEXT
            else -> CloudFileType.OTHER
        }
    }

    /**
     * Resolves a sensible MIME type if the provider left it empty or generic octet-stream.
     */
    fun resolveMimeType(fileName: String, existingMime: String? = null): String {
        if (!existingMime.isNullOrBlank() && existingMime != "application/octet-stream") {
            return existingMime
        }

        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "csv" -> "text/csv"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    fun getIcon(fileType: CloudFileType): ImageVector {
        return when (fileType) {
            CloudFileType.IMAGE -> Icons.Default.Image
            CloudFileType.VIDEO -> Icons.Default.Videocam
            CloudFileType.AUDIO -> Icons.Default.Audiotrack
            CloudFileType.DOCUMENT -> Icons.Default.Description
            CloudFileType.ARCHIVE -> Icons.Default.Archive
            CloudFileType.APK -> Icons.Default.Android
            CloudFileType.TEXT -> Icons.Default.Article
            CloudFileType.OTHER -> Icons.Default.InsertDriveFile
        }
    }

    fun getIconColor(fileType: CloudFileType): Color {
        return when (fileType) {
            CloudFileType.IMAGE -> Color(0xFF0288D1)
            CloudFileType.VIDEO -> Color(0xFFE53935)
            CloudFileType.AUDIO -> Color(0xFFFB8C00)
            CloudFileType.DOCUMENT -> Color(0xFF1E88E5)
            CloudFileType.ARCHIVE -> Color(0xFF8E24AA)
            CloudFileType.APK -> Color(0xFF43A047)
            CloudFileType.TEXT -> Color(0xFF00ACC1)
            CloudFileType.OTHER -> Color(0xFF757575)
        }
    }
}
