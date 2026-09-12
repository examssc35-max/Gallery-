package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.cloudflare.R2Client
import com.example.data.local.PreferencesManager
import com.example.data.local.R2Credentials
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.repository.CloudMediaPage
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class R2RepositoryImpl(
    private val context: Context,
    private val r2Client: R2Client,
    private val preferencesManager: PreferencesManager
) : R2Repository {

    override val credentialsFlow: Flow<R2Credentials> = preferencesManager.r2CredentialsFlow

    override suspend fun getCredentials(): R2Credentials {
        return preferencesManager.r2CredentialsFlow.first()
    }

    override suspend fun testConnection(customCredentials: R2Credentials?): Result<Boolean> {
        val creds = customCredentials ?: getCredentials()
        val result = r2Client.testConnection(creds)
        if (result.isSuccess && customCredentials == null) {
            preferencesManager.setR2Verified(true)
        }
        return result
    }

    override suspend fun listObjects(prefix: String): Result<List<R2Item>> {
        val creds = getCredentials()
        if (!creds.isVerified && creds.secretAccessKey.isEmpty()) {
            return Result.failure(IllegalStateException("Cloudflare R2 is not connected"))
        }
        return r2Client.listObjects(creds, prefix)
    }

    override suspend fun listCloudMediaPage(
        prefix: String,
        continuationToken: String?,
        pageSize: Int
    ): Result<CloudMediaPage> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        if (!creds.isVerified && creds.secretAccessKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Cloudflare R2 is not connected"))
        }

        val listResult = r2Client.listObjectsPage(
            credentials = creds,
            prefix = prefix,
            delimiter = "/",
            maxKeys = pageSize,
            continuationToken = continuationToken
        )

        listResult.map { r2Page ->
            val folders = mutableListOf<String>()
            val mediaItems = mutableListOf<MediaItem>()

            for (r2Item in r2Page.items) {
                if (r2Item.isFolder) {
                    val folderName = r2Item.name.removeSuffix("/")
                    if (folderName.isNotEmpty()) {
                        folders.add(r2Item.key)
                    }
                } else {
                    val name = r2Item.name
                    val isVideo = r2Item.mimeType.startsWith("video/") ||
                            name.endsWith(".mp4", ignoreCase = true) ||
                            name.endsWith(".mov", ignoreCase = true) ||
                            name.endsWith(".mkv", ignoreCase = true) ||
                            name.endsWith(".webm", ignoreCase = true) ||
                            name.endsWith(".3gp", ignoreCase = true)

                    val presignedUrl = try {
                        r2Client.getPresignedUrl(creds, r2Item.key, expiresSeconds = 86400)
                    } catch (_: Exception) {
                        ""
                    }

                    val stableId = (r2Item.key.hashCode().toLong() and 0x7FFFFFFFL) + 2_000_000_000L

                    mediaItems.add(
                        MediaItem(
                            id = stableId,
                            uriString = presignedUrl,
                            name = name,
                            path = r2Item.key,
                            size = r2Item.size,
                            dateAdded = if (r2Item.lastModified > 0) r2Item.lastModified / 1000 else System.currentTimeMillis() / 1000,
                            dateModified = if (r2Item.lastModified > 0) r2Item.lastModified else System.currentTimeMillis(),
                            mimeType = if (r2Item.mimeType.isNotEmpty()) r2Item.mimeType else if (isVideo) "video/mp4" else "image/jpeg",
                            isVideo = isVideo,
                            albumName = if (prefix.isEmpty()) "Cloud" else prefix.trimEnd('/'),
                            isFavorite = false,
                            isCloud = true,
                            cloudKey = r2Item.key
                        )
                    )
                }
            }

            CloudMediaPage(
                items = mediaItems,
                folders = folders,
                nextContinuationToken = r2Page.nextContinuationToken,
                isTruncated = r2Page.isTruncated
            )
        }
    }

    override suspend fun getPresignedUrl(key: String, expiresSeconds: Long): Result<String> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        if (creds.secretAccessKey.isEmpty() || creds.bucketName.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Cloudflare R2 credentials missing"))
        }
        try {
            val url = r2Client.getPresignedUrl(creds, key, expiresSeconds)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadMedia(
        item: MediaItem,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        if (creds.secretAccessKey.isEmpty() || creds.bucketName.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Cloudflare R2 credentials missing"))
        }

        val folder = if (item.isVideo) "videos/" else "photos/"
        val remoteKey = "$folder${item.name}"
        val uri = Uri.parse(item.uriString)

        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Cannot open media stream: ${e.message}"))
        } ?: return@withContext Result.failure(Exception("Stream is null for URI: $uri"))

        val uploadResult = r2Client.uploadStream(
            credentials = creds,
            key = remoteKey,
            inputStream = inputStream,
            contentLength = item.size,
            mimeType = item.mimeType,
            onProgress = onProgress
        )

        if (uploadResult.isSuccess) {
            Result.success(remoteKey)
        } else {
            Result.failure(uploadResult.exceptionOrNull() ?: Exception("Unknown upload error"))
        }
    }

    override suspend fun downloadObject(
        r2Item: R2Item,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        if (creds.secretAccessKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Cloudflare R2 credentials missing"))
        }
        r2Client.downloadToFile(creds, r2Item.key, destinationFile, onProgress)
    }

    override suspend fun downloadKeyToFile(
        key: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        if (creds.secretAccessKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Cloudflare R2 credentials missing"))
        }
        r2Client.downloadToFile(creds, key, destinationFile, onProgress)
    }

    override suspend fun deleteObject(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val creds = getCredentials()
        r2Client.deleteObject(creds, key)
    }

    override suspend fun saveCredentials(credentials: R2Credentials) {
        preferencesManager.saveR2Credentials(
            accountId = credentials.accountId,
            accessKeyId = credentials.accessKeyId,
            secretAccessKey = credentials.secretAccessKey,
            bucketName = credentials.bucketName,
            endpoint = credentials.endpoint,
            isVerified = credentials.isVerified
        )
    }

    override suspend fun disconnect() {
        preferencesManager.clearR2Credentials()
    }
}
