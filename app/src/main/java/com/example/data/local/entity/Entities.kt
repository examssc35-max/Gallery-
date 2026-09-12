package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uriString: String,
    val fileName: String,
    val fileSize: Long,
    val dateModified: Long,
    val remoteKey: String,
    val status: String,
    val errorMessage: String? = null,
    val lastBackupTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaStoreId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uriString: String,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val deletedAt: Long = System.currentTimeMillis()
)
