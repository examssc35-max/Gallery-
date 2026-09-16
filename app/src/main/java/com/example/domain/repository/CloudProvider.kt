package com.example.domain.repository

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.MultiCloudMediaPage
import java.io.File

interface CloudProvider {
    val providerId: String
    val displayName: String
    val capabilities: CloudCapabilities

    suspend fun getConnectionState(): CloudConnectionState
    suspend fun connect(params: Map<String, String>): Result<Boolean>
    suspend fun disconnect(): Result<Unit>
    suspend fun listMedia(folderId: String? = null, continuationToken: String? = null, pageSize: Int = 50): Result<MultiCloudMediaPage>
    suspend fun searchMedia(request: CloudSearchRequest): Result<List<CloudMediaItem>>
    suspend fun download(
        remoteId: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File>
    suspend fun upload(
        file: File,
        mimeType: String,
        targetFolder: String? = null,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<CloudMediaItem>
    suspend fun delete(remoteId: String): Result<Unit>
    suspend fun refresh(): Result<Unit>
    suspend fun getStorageUsage(): Result<CloudStorageQuota?>
}
