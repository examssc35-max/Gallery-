package com.example.domain.repository

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.MultiCloudMediaPage
import com.example.domain.model.multicloud.ProviderConnectionInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MultiCloudRepository {
    val providersInfoFlow: Flow<List<ProviderConnectionInfo>>
    fun getProvider(providerId: String): CloudProvider?
    fun getAllProviders(): List<CloudProvider>
    suspend fun getConnectedProviders(): List<CloudProvider>
    suspend fun connectProvider(providerId: String, params: Map<String, String>): Result<Boolean>
    suspend fun disconnectProvider(providerId: String): Result<Unit>
    suspend fun listMedia(
        providerFilter: String? = null,
        folderId: String? = null,
        continuationToken: String? = null,
        pageSize: Int = 50
    ): Result<MultiCloudMediaPage>
    suspend fun searchMedia(request: CloudSearchRequest): Result<List<CloudMediaItem>>
    suspend fun download(
        providerId: String,
        remoteId: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File>
    suspend fun upload(
        providerId: String,
        file: File,
        mimeType: String,
        targetFolder: String? = null,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<CloudMediaItem>
    suspend fun delete(providerId: String, remoteId: String): Result<Unit>
    suspend fun refreshAll(): Result<Unit>
    suspend fun getAllStorageQuotas(): List<CloudStorageQuota>
}
