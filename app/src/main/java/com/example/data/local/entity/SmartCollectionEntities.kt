package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "smart_classifications",
    indices = [
        Index(value = ["mediaStoreId", "categoryId"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["mediaStoreId"])
    ]
)
data class SmartClassificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val categoryId: String,
    val categoryDisplayName: String,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    val modelVersion: String = "v1.0"
)

@Entity(tableName = "media_analysis_states")
data class MediaAnalysisStateEntity(
    @PrimaryKey val mediaStoreId: Long,
    val status: String, // "NOT_ANALYZED", "ANALYZING", "ANALYZED", "FAILED", "OUTDATED"
    val dateModified: Long,
    val lastAnalyzedAt: Long = System.currentTimeMillis(),
    val modelVersion: String = "v1.0",
    val errorMessage: String? = null
)
