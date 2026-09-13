package com.example.domain.usecase

import com.example.domain.model.StorageUsage
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.flow.Flow

class GetStorageUsageUseCase(
    private val r2Repository: R2Repository
) {
    val cachedStorageUsageFlow: Flow<StorageUsage?> = r2Repository.cachedStorageUsageFlow

    suspend fun getCachedStorageUsage(): StorageUsage? {
        return r2Repository.getCachedStorageUsage()
    }

    suspend fun calculateStorageUsage(
        onProgress: (scannedObjects: Int, scannedBytes: Long) -> Unit = { _, _ -> }
    ): Result<StorageUsage> {
        return r2Repository.calculateStorageUsage(onProgress)
    }
}
