package com.example.ai.tools

import android.content.Context
import com.example.ai.model.AiActionResult
import com.example.ai.model.CloudItemSummary
import com.example.ai.model.MediaItemSummary
import com.example.data.local.PreferencesManager
import com.example.data.local.SortOrder
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.R2Repository
import com.example.domain.repository.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GalleryAssistantToolsImpl(
    private val mediaRepository: MediaRepository,
    private val backupRepository: BackupRepository,
    private val r2Repository: R2Repository,
    private val preferencesManager: PreferencesManager,
    private val context: Context
) : GalleryAssistantTools {

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown date"
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override suspend fun searchLocalMedia(
        query: String?,
        mediaType: String?,
        isFavorite: Boolean?,
        minSizeBytes: Long?,
        maxSizeBytes: Long?,
        albumName: String?,
        dateFilter: DateFilterType
    ): ToolResult<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val allItems = mediaRepository.loadMediaItems()
            val cal = getCurrentDayStart()

            val filtered = allItems.filter { item ->
                // Query match
                val matchesQuery = query.isNullOrBlank() ||
                        item.name.contains(query, ignoreCase = true) ||
                        item.albumName.contains(query, ignoreCase = true)

                // Media type match
                val matchesType = when (mediaType?.lowercase()) {
                    "video", "videos" -> item.isVideo
                    "photo", "photos", "image", "images" -> !item.isVideo
                    else -> true
                }

                // Favorite match
                val matchesFav = isFavorite == null || item.isFavorite == isFavorite

                // Size match
                val matchesMinSize = minSizeBytes == null || item.size >= minSizeBytes
                val matchesMaxSize = maxSizeBytes == null || item.size <= maxSizeBytes

                // Album name match
                val matchesAlbum = albumName.isNullOrBlank() ||
                        item.albumName.contains(albumName, ignoreCase = true)

                // Date filter
                val matchesDate = when (dateFilter) {
                    DateFilterType.ALL -> true
                    DateFilterType.TODAY -> item.dateAdded >= cal.timeInMillis
                    DateFilterType.YESTERDAY -> {
                        val yesterdayStart = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
                        item.dateAdded in yesterdayStart until cal.timeInMillis
                    }
                    DateFilterType.THIS_WEEK -> {
                        val weekStart = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
                        item.dateAdded >= weekStart
                    }
                    DateFilterType.THIS_MONTH -> {
                        val monthStart = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis
                        item.dateAdded >= monthStart
                    }
                }

                matchesQuery && matchesType && matchesFav && matchesMinSize && matchesMaxSize && matchesAlbum && matchesDate
            }

            val desc = buildString {
                append("Found ${filtered.size} ")
                if (mediaType != null) append("$mediaType ") else append("items ")
                if (!query.isNullOrBlank()) append("matching '$query' ")
                if (!albumName.isNullOrBlank()) append("in '$albumName' ")
                if (minSizeBytes != null) append("larger than ${formatBytes(minSizeBytes)} ")
            }.trim()

            val actionResult = AiActionResult.MediaListResult(
                title = if (mediaType?.contains("video", ignoreCase = true) == true) "Videos Found" else "Photos & Media Found",
                items = filtered.take(100),
                totalCount = filtered.size,
                filterDescription = desc
            )

            ToolResult.Success(filtered, actionResult)
        } catch (e: Exception) {
            ToolResult.Error("Error searching local media: ${e.message}", e)
        }
    }

    override suspend fun searchCloudMedia(
        query: String?,
        category: MediaCategory?,
        prefix: String
    ): ToolResult<List<R2Item>> = withContext(Dispatchers.IO) {
        try {
            val creds = r2Repository.getCredentials()
            if (creds.bucketName.isBlank() || creds.accessKeyId.isBlank()) {
                return@withContext ToolResult.Error("Cloudflare R2 is not configured yet. Please configure credentials in Settings.")
            }

            val result = r2Repository.listObjects(prefix)
            if (result.isFailure) {
                return@withContext ToolResult.Error("Failed to access R2 bucket: ${result.exceptionOrNull()?.message}")
            }

            val items = result.getOrNull() ?: emptyList()
            val filtered = items.filter { item ->
                if (item.isFolder) return@filter false
                val matchesQuery = query.isNullOrBlank() || item.name.contains(query, ignoreCase = true)
                val matchesCategory = if (category != null) {
                    val isVid = item.name.endsWith(".mp4", true) || item.name.endsWith(".mkv", true) ||
                            item.name.endsWith(".mov", true) || item.name.endsWith(".webm", true)
                    val isImg = item.name.endsWith(".jpg", true) || item.name.endsWith(".jpeg", true) ||
                            item.name.endsWith(".png", true) || item.name.endsWith(".webp", true) ||
                            item.name.endsWith(".heic", true)
                    when (category) {
                        MediaCategory.VIDEO -> isVid
                        MediaCategory.PHOTO -> isImg
                        MediaCategory.OTHER -> !isVid && !isImg
                    }
                } else true

                matchesQuery && matchesCategory
            }

            val actionResult = AiActionResult.CloudListResult(
                title = "Cloud Files in Bucket '${creds.bucketName}'",
                items = filtered.take(100),
                totalCount = filtered.size
            )

            ToolResult.Success(filtered, actionResult)
        } catch (e: Exception) {
            ToolResult.Error("Failed to search cloud media: ${e.message}", e)
        }
    }

    override suspend fun getStorageUsage(): ToolResult<AiActionResult.StorageUsageResult> = withContext(Dispatchers.IO) {
        try {
            val cachedUsage = r2Repository.getCachedStorageUsage()
            val localItems = mediaRepository.loadMediaItems()
            val localStats = mediaRepository.calculateStorageStats(localItems, emptyList())

            val summaryText = buildString {
                append("Local Device: ${localStats.totalCount} items (${formatBytes(localStats.totalBytes)})\n")
                append("• Photos: ${localStats.photoCount} (${formatBytes(localStats.photoBytes)})\n")
                append("• Videos: ${localStats.videoCount} (${formatBytes(localStats.videoBytes)})\n")
                if (cachedUsage != null) {
                    append("\nCloudflare R2 Bucket: ${cachedUsage.totalObjectCount} objects (${formatBytes(cachedUsage.totalBytes)})\n")
                    append("• Photos: ${cachedUsage.photoCount} (${formatBytes(cachedUsage.photoBytes)})\n")
                    append("• Videos: ${cachedUsage.videoCount} (${formatBytes(cachedUsage.videoBytes)})\n")
                    append("• Other: ${cachedUsage.otherCount} (${formatBytes(cachedUsage.otherBytes)})")
                } else {
                    append("\nCloudflare R2: No storage usage cached yet. Tap 'Recalculate' in Cloud Storage to scan.")
                }
            }

            val actionResult = AiActionResult.StorageUsageResult(
                storageUsage = cachedUsage,
                localStorageStats = localStats,
                formattedSummary = summaryText
            )

            ToolResult.Success(actionResult, actionResult)
        } catch (e: Exception) {
            ToolResult.Error("Failed to calculate storage usage: ${e.message}", e)
        }
    }

    override suspend fun getBackupStatus(): ToolResult<AiActionResult.BackupStatusResult> = withContext(Dispatchers.IO) {
        try {
            val stats = backupRepository.getBackupStats()
            val settings = backupRepository.getBackupSettings()

            val lastTimeFormatted = if (settings.lastBackupTime > 0) {
                formatDate(settings.lastBackupTime)
            } else {
                "Never"
            }

            val actionResult = AiActionResult.BackupStatusResult(
                backedUpCount = stats.backedUpCount,
                pendingCount = stats.pendingCount,
                failedCount = stats.failedCount,
                isAutoBackupEnabled = settings.isAutoBackupEnabled,
                lastBackupTimeFormatted = lastTimeFormatted,
                wifiOnly = settings.wifiOnly
            )

            ToolResult.Success(actionResult, actionResult)
        } catch (e: Exception) {
            ToolResult.Error("Failed to fetch backup status: ${e.message}", e)
        }
    }

    override suspend fun startBackup(onlyVideos: Boolean): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            val creds = r2Repository.getCredentials()
            if (!creds.isVerified && creds.bucketName.isBlank()) {
                return@withContext ToolResult.Error("Cloudflare R2 is not configured or verified. Please connect R2 in Settings before starting a backup.")
            }

            if (onlyVideos) {
                val current = backupRepository.getBackupSettings()
                backupRepository.updateBackupSettings(
                    current.copy(backupVideos = true, backupPhotos = false)
                )
            }

            val uploadedCount = backupRepository.runBackupPass()
            val message = if (uploadedCount > 0) {
                "Successfully backed up $uploadedCount new item(s) to Cloudflare R2."
            } else {
                "All items are already backed up! No pending uploads found."
            }

            ToolResult.Success(message, AiActionResult.SimpleActionResult(
                title = "Backup Run Completed",
                message = message,
                isSuccess = true
            ))
        } catch (e: Exception) {
            ToolResult.Error("Backup operation encountered an error: ${e.message}", e)
        }
    }

    override suspend fun getUploadStatus(): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            val stats = backupRepository.getBackupStats()
            val message = "Current Upload Status:\n• Uploading now: ${stats.uploadingCount}\n• Pending: ${stats.pendingCount}\n• Backed up: ${stats.backedUpCount}\n• Failed: ${stats.failedCount}"
            ToolResult.Success(message)
        } catch (e: Exception) {
            ToolResult.Error("Failed to get upload status: ${e.message}", e)
        }
    }

    override suspend fun downloadCloudFile(key: String): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            val safeName = key.substringAfterLast("/")
            val downloadDir = File(context.cacheDir, "cloud_downloads").apply { mkdirs() }
            val destFile = File(downloadDir, safeName)

            val result = r2Repository.downloadKeyToFile(key, destFile)
            if (result.isSuccess) {
                ToolResult.Success(
                    "File '$safeName' (${formatBytes(destFile.length())}) was successfully downloaded.",
                    AiActionResult.SimpleActionResult(
                        title = "Download Complete",
                        message = "Downloaded '$safeName' (${formatBytes(destFile.length())})",
                        isSuccess = true
                    )
                )
            } else {
                ToolResult.Error("Download failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to download cloud file: ${e.message}", e)
        }
    }

    override suspend fun requestDelete(items: List<MediaItem>): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) {
                return@withContext ToolResult.Success("No items selected for deletion.")
            }
            val result = mediaRepository.deleteMedia(items)
            val message = "Requested deletion for ${items.size} item(s)."
            ToolResult.Success(message, AiActionResult.SimpleActionResult(
                title = "Deletion Requested",
                message = message,
                isSuccess = true
            ))
        } catch (e: Exception) {
            ToolResult.Error("Failed to request deletion: ${e.message}", e)
        }
    }

    override suspend fun refreshGallery(): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            val items = mediaRepository.loadMediaItems()
            val message = "Gallery refreshed! Found ${items.size} media items on device."
            ToolResult.Success(message, AiActionResult.SimpleActionResult(
                title = "Gallery Refreshed",
                message = message,
                isSuccess = true
            ))
        } catch (e: Exception) {
            ToolResult.Error("Failed to refresh gallery: ${e.message}", e)
        }
    }

    override suspend fun openMedia(mediaId: Long): ToolResult<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val items = mediaRepository.loadMediaItems()
            val match = items.firstOrNull { it.id == mediaId }
            if (match != null) {
                ToolResult.Success(match)
            } else {
                ToolResult.Error("Media item with ID $mediaId not found.")
            }
        } catch (e: Exception) {
            ToolResult.Error("Error opening media: ${e.message}", e)
        }
    }

    override suspend fun openAlbum(albumName: String): ToolResult<String> = withContext(Dispatchers.IO) {
        try {
            val items = mediaRepository.loadMediaItems()
            val albums = mediaRepository.getAlbums(items)
            val match = albums.firstOrNull { it.name.equals(albumName, ignoreCase = true) || it.name.contains(albumName, ignoreCase = true) }
            if (match != null) {
                ToolResult.Success("Album '${match.name}' found with ${match.itemCount} items.", AiActionResult.SimpleActionResult(
                    title = "Album: ${match.name}",
                    message = "Contains ${match.itemCount} items.",
                    isSuccess = true,
                    navigateRoute = "album/${match.name}"
                ))
            } else {
                val names = albums.take(5).joinToString { "'${it.name}'" }
                ToolResult.Error("Album '$albumName' not found. Available albums: $names")
            }
        } catch (e: Exception) {
            ToolResult.Error("Error opening album: ${e.message}", e)
        }
    }

    override suspend fun filterMedia(tabName: String, query: String?): ToolResult<String> = withContext(Dispatchers.IO) {
        val msg = "Filter applied: Tab=$tabName${if (!query.isNullOrBlank()) ", Query='$query'" else ""}"
        ToolResult.Success(msg)
    }

    override suspend fun sortMedia(sortOrder: SortOrder): ToolResult<String> = withContext(Dispatchers.IO) {
        preferencesManager.setSortOrder(sortOrder)
        val name = when (sortOrder) {
            SortOrder.DATE_DESC -> "Newest First"
            SortOrder.DATE_ASC -> "Oldest First"
            SortOrder.NAME_ASC -> "Name (A-Z)"
            SortOrder.NAME_DESC -> "Name (Z-A)"
            SortOrder.SIZE_DESC -> "Largest First"
        }
        ToolResult.Success("Gallery sort order changed to: $name")
    }

    override suspend fun getFileDetails(mediaId: Long): ToolResult<MediaItemSummary> = withContext(Dispatchers.IO) {
        try {
            val items = mediaRepository.loadMediaItems()
            val match = items.firstOrNull { it.id == mediaId }
            if (match != null) {
                val summary = MediaItemSummary(
                    id = match.id,
                    displayName = match.name,
                    sizeBytes = match.size,
                    formattedSize = formatBytes(match.size),
                    mimeType = match.mimeType,
                    isVideo = match.isVideo,
                    dateAdded = match.dateAdded,
                    formattedDate = formatDate(match.dateAdded),
                    albumName = match.albumName,
                    uriString = match.uriString
                )
                ToolResult.Success(summary)
            } else {
                ToolResult.Error("File with ID $mediaId not found.")
            }
        } catch (e: Exception) {
            ToolResult.Error("Error retrieving file details: ${e.message}", e)
        }
    }

    override suspend fun testR2Connection(): ToolResult<AiActionResult.R2ConnectionResult> = withContext(Dispatchers.IO) {
        try {
            val creds = r2Repository.getCredentials()
            if (creds.bucketName.isBlank() || creds.accessKeyId.isBlank() || creds.secretAccessKey.isBlank()) {
                val result = AiActionResult.R2ConnectionResult(
                    isConnected = false,
                    bucketName = creds.bucketName.ifBlank { "Not set" },
                    message = "Cloudflare R2 is not configured yet. Add your Account ID, Access Key ID, Secret Key, and Bucket in Settings."
                )
                return@withContext ToolResult.Success(result, result)
            }

            val testRes = r2Repository.testConnection()
            val isSuccess = testRes.getOrDefault(false)
            val msg = if (isSuccess) {
                "Successfully connected to Cloudflare R2 bucket '${creds.bucketName}'!"
            } else {
                "Failed to connect to R2: ${testRes.exceptionOrNull()?.message ?: "Check bucket name, credentials, or network connection."}"
            }

            val result = AiActionResult.R2ConnectionResult(
                isConnected = isSuccess,
                bucketName = creds.bucketName,
                message = msg
            )
            ToolResult.Success(result, result)
        } catch (e: Exception) {
            val result = AiActionResult.R2ConnectionResult(
                isConnected = false,
                bucketName = "",
                message = "Connection test error: ${e.message}"
            )
            ToolResult.Success(result, result)
        }
    }

    override suspend fun explainUploadFailure(): ToolResult<AiActionResult.FailureDiagnosticResult> = withContext(Dispatchers.IO) {
        try {
            val creds = r2Repository.getCredentials()
            val settings = backupRepository.getBackupSettings()
            val stats = backupRepository.getBackupStats()

            val causes = mutableListOf<String>()
            val fixes = mutableListOf<String>()

            if (creds.bucketName.isBlank() || creds.accessKeyId.isBlank() || creds.secretAccessKey.isBlank()) {
                causes.add("R2 credentials or bucket name are missing.")
                fixes.add("Go to Settings → Cloudflare R2 and configure your credentials.")
            } else if (!creds.isVerified) {
                causes.add("R2 credentials have not been verified.")
                fixes.add("In Settings, tap 'Test Connection' to verify bucket permissions.")
            }

            if (settings.wifiOnly) {
                causes.add("Backup is set to 'Wi-Fi Only' and device might be on mobile data.")
                fixes.add("Connect to Wi-Fi or disable 'Wi-Fi Only' in Backup Settings.")
            }

            if (settings.requireCharging) {
                causes.add("Backup requires the device to be charging.")
                fixes.add("Plug in your phone to charge or disable 'Require Charging' in Backup Settings.")
            }

            if (stats.failedCount > 0) {
                causes.add("${stats.failedCount} upload(s) previously encountered an error or network timeout.")
                fixes.add("Tap 'Retry Failed Uploads' in the Backup Screen or say 'Start backup'.")
            }

            if (causes.isEmpty()) {
                causes.add("No critical configuration issues detected.")
                fixes.add("Verify device internet connectivity and check Cloudflare R2 service status.")
            }

            val diag = AiActionResult.FailureDiagnosticResult(
                title = "Upload Diagnostic Report",
                causes = causes,
                recommendedFixes = fixes
            )
            ToolResult.Success(diag, diag)
        } catch (e: Exception) {
            ToolResult.Error("Failed to run diagnostics: ${e.message}", e)
        }
    }

    override suspend fun findDuplicates(): ToolResult<AiActionResult.DuplicatesResult> = withContext(Dispatchers.IO) {
        try {
            val items = mediaRepository.loadMediaItems()
            val duplicateGroups = mediaRepository.findDuplicates(items)
            val dupItemsCount = duplicateGroups.sumOf { it.items.size - 1 }
            val wastedBytes = duplicateGroups.sumOf { (it.items.size - 1) * it.size }

            val actionResult = AiActionResult.DuplicatesResult(
                groupCount = duplicateGroups.size,
                duplicateItemsCount = dupItemsCount,
                totalWastedBytes = wastedBytes,
                formattedWastedSize = formatBytes(wastedBytes)
            )
            ToolResult.Success(actionResult, actionResult)
        } catch (e: Exception) {
            ToolResult.Error("Failed to find duplicates: ${e.message}", e)
        }
    }

    private fun getCurrentDayStart(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
