package com.example.data.multicloud

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudOperation
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.MultiCloudMediaPage
import com.example.domain.repository.CloudProvider
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.flow.first
import java.io.File

class CloudflareR2Provider(
    private val r2Repository: R2Repository
) : CloudProvider {

    override val providerId: String = "r2"
    override val displayName: String = "Cloudflare R2"

    override val capabilities: CloudCapabilities = CloudCapabilities(
        canBrowse = true,
        canSearch = true,
        canUpload = true,
        canDownload = true,
        canDelete = true,
        canRename = false,
        canMove = false,
        canCreateFolder = true,
        supportsThumbnails = true,
        supportsStorageUsage = true,
        supportsBackgroundSync = true
    )

    override suspend fun getConnectionState(): CloudConnectionState {
        return try {
            val creds = r2Repository.getCredentials()
            if (creds.isVerified && creds.secretAccessKey.isNotEmpty() && creds.bucketName.isNotEmpty()) {
                CloudConnectionState.Connected(
                    accountName = "Bucket: ${creds.bucketName}",
                    serviceInfo = if (creds.endpoint.isNotEmpty()) creds.endpoint else "R2 S3 API"
                )
            } else {
                CloudConnectionState.NotConnected
            }
        } catch (_: Exception) {
            CloudConnectionState.NotConnected
        }
    }

    override suspend fun connect(params: Map<String, String>): Result<Boolean> {
        return try {
            val accountId = params["accountId"] ?: ""
            val accessKeyId = params["accessKeyId"] ?: ""
            val secretAccessKey = params["secretAccessKey"] ?: ""
            val bucketName = params["bucketName"] ?: ""
            val endpoint = params["endpoint"] ?: ""

            val creds = com.example.data.local.R2Credentials(
                accountId = accountId,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                bucketName = bucketName,
                endpoint = endpoint,
                isVerified = false
            )
            val testResult = r2Repository.testConnection(creds)
            if (testResult.isSuccess && testResult.getOrDefault(false)) {
                r2Repository.saveCredentials(creds.copy(isVerified = true))
                Result.success(true)
            } else {
                Result.failure(testResult.exceptionOrNull() ?: Exception("Failed to connect to Cloudflare R2"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        return try {
            r2Repository.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listMedia(
        folderId: String?,
        continuationToken: String?,
        pageSize: Int
    ): Result<MultiCloudMediaPage> {
        return try {
            val prefix = folderId ?: ""
            val pageResult = r2Repository.listCloudMediaPage(
                prefix = prefix,
                continuationToken = continuationToken,
                pageSize = pageSize
            )

            pageResult.map { r2Page ->
                val cloudItems = r2Page.items.map { item ->
                    CloudMediaItem(
                        providerId = providerId,
                        providerName = displayName,
                        remoteId = item.cloudKey ?: item.name,
                        name = item.name,
                        mimeType = item.mimeType,
                        size = item.size,
                        createdAt = item.dateAdded,
                        modifiedAt = item.dateModified,
                        thumbnailUrl = item.uriString,
                        downloadUrl = item.uriString,
                        isVideo = item.isVideo,
                        durationMs = item.durationMs,
                        width = item.width,
                        height = item.height,
                        isFolder = false,
                        folderPath = item.path,
                        capabilities = capabilities
                    )
                }

                MultiCloudMediaPage(
                    items = cloudItems,
                    folders = r2Page.folders,
                    nextContinuationToken = r2Page.nextContinuationToken,
                    isTruncated = r2Page.isTruncated
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMedia(request: CloudSearchRequest): Result<List<CloudMediaItem>> {
        return try {
            val objectsResult = r2Repository.listObjects()
            objectsResult.map { r2Items ->
                r2Items.filter { !it.isFolder }
                    .filter { item ->
                        val matchesQuery = request.query.isNullOrEmpty() ||
                                item.name.contains(request.query, ignoreCase = true) ||
                                item.key.contains(request.query, ignoreCase = true)

                        val isVideo = item.mimeType.startsWith("video/") ||
                                item.name.endsWith(".mp4", ignoreCase = true) ||
                                item.name.endsWith(".mov", ignoreCase = true) ||
                                item.name.endsWith(".mkv", ignoreCase = true)

                        val matchesType = when (request.mediaType?.lowercase()) {
                            "video", "videos" -> isVideo
                            "photo", "photos", "image", "images" -> !isVideo
                            else -> true
                        }

                        val matchesSize = request.minSizeBytes == null || item.size >= request.minSizeBytes

                        matchesQuery && matchesType && matchesSize
                    }
                    .map { item ->
                        val isVideo = item.mimeType.startsWith("video/") ||
                                item.name.endsWith(".mp4", ignoreCase = true) ||
                                item.name.endsWith(".mov", ignoreCase = true)
                        val presigned = r2Repository.getPresignedUrl(item.key).getOrNull()

                        CloudMediaItem(
                            providerId = providerId,
                            providerName = displayName,
                            remoteId = item.key,
                            name = item.name,
                            mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                            size = item.size,
                            createdAt = item.lastModified,
                            modifiedAt = item.lastModified,
                            thumbnailUrl = presigned,
                            downloadUrl = presigned,
                            isVideo = isVideo,
                            capabilities = capabilities
                        )
                    }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(
        remoteId: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> {
        return r2Repository.downloadKeyToFile(remoteId, destinationFile, onProgress)
    }

    override suspend fun upload(
        file: File,
        mimeType: String,
        targetFolder: String?,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<CloudMediaItem> {
        return try {
            val isVideo = mimeType.startsWith("video/")
            val key = if (!targetFolder.isNullOrEmpty()) {
                val cleanFolder = targetFolder.trimEnd('/')
                "$cleanFolder/${file.name}"
            } else {
                file.name
            }

            val tempMedia = com.example.domain.model.MediaItem(
                id = System.currentTimeMillis(),
                uriString = file.toURI().toString(),
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                dateAdded = System.currentTimeMillis(),
                dateModified = file.lastModified(),
                mimeType = mimeType,
                isVideo = isVideo,
                cloudKey = key
            )

            val uploadRes = r2Repository.uploadMedia(tempMedia, onProgress)
            if (uploadRes.isSuccess) {
                val presigned = r2Repository.getPresignedUrl(key).getOrNull()
                Result.success(
                    CloudMediaItem(
                        providerId = providerId,
                        providerName = displayName,
                        remoteId = key,
                        name = file.name,
                        mimeType = mimeType,
                        size = file.length(),
                        createdAt = System.currentTimeMillis(),
                        modifiedAt = System.currentTimeMillis(),
                        thumbnailUrl = presigned,
                        downloadUrl = presigned,
                        isVideo = isVideo,
                        capabilities = capabilities
                    )
                )
            } else {
                Result.failure(uploadRes.exceptionOrNull() ?: Exception("Upload to R2 failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(remoteId: String): Result<Unit> {
        return r2Repository.deleteObject(remoteId)
    }

    override suspend fun refresh(): Result<Unit> {
        return try {
            r2Repository.calculateStorageUsage()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStorageUsage(): Result<CloudStorageQuota?> {
        return try {
            val usage = r2Repository.getCachedStorageUsage()
                ?: r2Repository.calculateStorageUsage().getOrNull()

            Result.success(
                CloudStorageQuota(
                    providerId = providerId,
                    providerName = displayName,
                    usedBytes = usage?.totalBytes,
                    totalBytes = null, // R2 has essentially unmetered capacity
                    isAvailable = usage != null,
                    statusMessage = usage?.let { "${it.totalObjectCount} objects scanned" }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
