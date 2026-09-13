package com.example.ai.model

import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.model.StorageStats
import com.example.domain.model.StorageUsage
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class AiProviderType {
    ON_DEVICE,
    GEMINI_BYOK
}

/**
 * Sanitized media summary exposed to the assistant.
 * Note: NEVER contains file bytes or unauthorized paths.
 */
data class MediaItemSummary(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAdded: Long,
    val formattedDate: String,
    val albumName: String? = null,
    val uriString: String
)

data class CloudItemSummary(
    val key: String,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModified: Long,
    val isVideo: Boolean
)

/**
 * Structured action results returned by safe tools and displayed in chat UI.
 */
sealed class AiActionResult {
    data class MediaListResult(
        val title: String,
        val items: List<MediaItem>,
        val totalCount: Int,
        val filterDescription: String
    ) : AiActionResult()

    data class CloudListResult(
        val title: String,
        val items: List<R2Item>,
        val totalCount: Int
    ) : AiActionResult()

    data class StorageUsageResult(
        val storageUsage: StorageUsage?,
        val localStorageStats: StorageStats? = null,
        val formattedSummary: String
    ) : AiActionResult()

    data class BackupStatusResult(
        val backedUpCount: Int,
        val pendingCount: Int,
        val failedCount: Int,
        val isAutoBackupEnabled: Boolean,
        val lastBackupTimeFormatted: String,
        val wifiOnly: Boolean
    ) : AiActionResult()

    data class R2ConnectionResult(
        val isConnected: Boolean,
        val bucketName: String,
        val message: String
    ) : AiActionResult()

    data class DuplicatesResult(
        val groupCount: Int,
        val duplicateItemsCount: Int,
        val totalWastedBytes: Long,
        val formattedWastedSize: String
    ) : AiActionResult()

    data class FailureDiagnosticResult(
        val title: String,
        val causes: List<String>,
        val recommendedFixes: List<String>
    ) : AiActionResult()

    data class SimpleActionResult(
        val title: String,
        val message: String,
        val isSuccess: Boolean = true,
        val navigateRoute: String? = null
    ) : AiActionResult()
}

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionResult: AiActionResult? = null,
    val suggestedActions: List<String> = emptyList(),
    val isThinking: Boolean = false
)
