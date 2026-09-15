package com.example.ai.classifier

import android.net.Uri
import com.example.domain.model.MediaClassificationResult

/**
 * Pluggable abstraction for Smart Collections visual classification.
 *
 * Privacy & Safety Guarantees:
 * - 100% On-Device execution
 * - No user image/video bytes sent over network
 * - No facial recognition or real-person identification
 * - Structured categories output with confidence scores
 */
interface SmartCollectionClassifier {
    val modelVersion: String
    val modelDisplayName: String
    val modelSizeFormatted: String

    suspend fun isModelInstalled(): Boolean

    suspend fun installModel(onProgress: (progress: Float) -> Unit): Result<Unit>

    suspend fun classifyImage(
        mediaId: Long,
        uri: Uri,
        mimeType: String,
        width: Int,
        height: Int,
        dateAdded: Long
    ): MediaClassificationResult

    suspend fun classifyVideo(
        mediaId: Long,
        uri: Uri,
        durationMs: Long
    ): MediaClassificationResult
}
