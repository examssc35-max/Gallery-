package com.example.data.repository

import com.example.data.multicloud.CloudflareR2Provider
import com.example.data.multicloud.DropboxProvider
import com.example.data.multicloud.GooglePhotosProvider
import com.example.data.multicloud.OneDriveProvider
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.MultiCloudMediaPage
import com.example.domain.model.multicloud.ProviderConnectionInfo
import com.example.domain.repository.CloudProvider
import com.example.domain.repository.MultiCloudRepository
import com.example.domain.repository.R2Repository
import com.example.security.SecureCloudTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MultiCloudRepositoryImpl(
    r2Repository: R2Repository,
    tokenStorage: SecureCloudTokenStorage
) : MultiCloudRepository {

    private val r2Provider = CloudflareR2Provider(r2Repository)
    private val googlePhotosProvider = GooglePhotosProvider(tokenStorage)
    private val oneDriveProvider = OneDriveProvider(tokenStorage)
    private val dropboxProvider = DropboxProvider(tokenStorage)

    private val providersList = listOf<CloudProvider>(
        r2Provider,
        googlePhotosProvider,
        oneDriveProvider,
        dropboxProvider
    )

    private val _providersInfoFlow = MutableStateFlow<List<ProviderConnectionInfo>>(emptyList())
    override val providersInfoFlow: Flow<List<ProviderConnectionInfo>> = _providersInfoFlow.asStateFlow()

    suspend fun refreshProvidersStatus() = withContext(Dispatchers.IO) {
        val list = providersList.map { provider ->
            val state = provider.getConnectionState()
            val quota = if (state.isConnected) provider.getStorageUsage().getOrNull() else null
            ProviderConnectionInfo(
                providerId = provider.providerId,
                displayName = provider.displayName,
                connectionState = state,
                capabilities = provider.capabilities,
                storageQuota = quota
            )
        }
        _providersInfoFlow.value = list
    }

    override fun getProvider(providerId: String): CloudProvider? {
        return providersList.find { it.providerId.equals(providerId, ignoreCase = true) }
    }

    override fun getAllProviders(): List<CloudProvider> = providersList

    override suspend fun getConnectedProviders(): List<CloudProvider> = withContext(Dispatchers.IO) {
        providersList.filter { it.getConnectionState().isConnected }
    }

    override suspend fun connectProvider(providerId: String, params: Map<String, String>): Result<Boolean> {
        val provider = getProvider(providerId)
            ?: return Result.failure(IllegalArgumentException("Unknown cloud provider: $providerId"))

        val res = provider.connect(params)
        if (res.isSuccess) {
            refreshProvidersStatus()
        }
        return res
    }

    override suspend fun disconnectProvider(providerId: String): Result<Unit> {
        val provider = getProvider(providerId)
            ?: return Result.failure(IllegalArgumentException("Unknown cloud provider: $providerId"))

        val res = provider.disconnect()
        refreshProvidersStatus()
        return res
    }

    override suspend fun listMedia(
        providerFilter: String?,
        folderId: String?,
        continuationToken: String?,
        pageSize: Int
    ): Result<MultiCloudMediaPage> = withContext(Dispatchers.IO) {
        try {
            if (!providerFilter.isNullOrEmpty() && providerFilter != "all") {
                val provider = getProvider(providerFilter)
                    ?: return@withContext Result.failure(IllegalArgumentException("Provider $providerFilter not found"))
                return@withContext provider.listMedia(folderId, continuationToken, pageSize)
            }

            // If no specific provider is filtered, aggregate connected providers
            val connected = getConnectedProviders()
            if (connected.isEmpty()) {
                return@withContext Result.success(MultiCloudMediaPage(emptyList()))
            }

            val deferredResults = connected.map { provider ->
                async {
                    provider.listMedia(folderId = null, continuationToken = null, pageSize = pageSize)
                }
            }

            val pages = deferredResults.awaitAll()
            val allItems = mutableListOf<CloudMediaItem>()
            val allFolders = mutableListOf<String>()

            for (pageRes in pages) {
                if (pageRes.isSuccess) {
                    val p = pageRes.getOrThrow()
                    allItems.addAll(p.items)
                    allFolders.addAll(p.folders)
                }
            }

            // Sort all cloud media by modifiedAt / createdAt descending
            allItems.sortByDescending { if (it.modifiedAt > 0) it.modifiedAt else it.createdAt }

            Result.success(
                MultiCloudMediaPage(
                    items = allItems,
                    folders = allFolders.distinct(),
                    nextContinuationToken = null,
                    isTruncated = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMedia(request: CloudSearchRequest): Result<List<CloudMediaItem>> = withContext(Dispatchers.IO) {
        try {
            if (!request.providerId.isNullOrEmpty() && request.providerId != "all") {
                val provider = getProvider(request.providerId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Provider ${request.providerId} not found"))
                return@withContext provider.searchMedia(request)
            }

            val connected = getConnectedProviders()
            if (connected.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val deferred = connected.map { provider ->
                async {
                    provider.searchMedia(request)
                }
            }

            val results = deferred.awaitAll()
            val allItems = mutableListOf<CloudMediaItem>()
            for (res in results) {
                if (res.isSuccess) {
                    allItems.addAll(res.getOrThrow())
                }
            }

            allItems.sortByDescending { if (it.modifiedAt > 0) it.modifiedAt else it.createdAt }
            Result.success(allItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(
        providerId: String,
        remoteId: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> {
        val provider = getProvider(providerId)
            ?: return Result.failure(IllegalArgumentException("Provider $providerId not found"))
        return provider.download(remoteId, destinationFile, onProgress)
    }

    override suspend fun upload(
        providerId: String,
        file: File,
        mimeType: String,
        targetFolder: String?,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<CloudMediaItem> {
        val provider = getProvider(providerId)
            ?: return Result.failure(IllegalArgumentException("Provider $providerId not found"))
        return provider.upload(file, mimeType, targetFolder, onProgress)
    }

    override suspend fun delete(providerId: String, remoteId: String): Result<Unit> {
        val provider = getProvider(providerId)
            ?: return Result.failure(IllegalArgumentException("Provider $providerId not found"))
        return provider.delete(remoteId)
    }

    override suspend fun refreshAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connected = getConnectedProviders()
            connected.forEach { it.refresh() }
            refreshProvidersStatus()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllStorageQuotas(): List<CloudStorageQuota> = withContext(Dispatchers.IO) {
        val connected = getConnectedProviders()
        connected.mapNotNull { provider ->
            provider.getStorageUsage().getOrNull()
        }
    }
}
