package com.example.data.multicloud

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudFileType
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.MultiCloudMediaPage
import com.example.domain.repository.CloudProvider
import com.example.security.OAuthManager
import com.example.security.SecureCloudTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OneDriveProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : CloudProvider {

    override val providerId: String = "onedrive"
    override val displayName: String = "Microsoft OneDrive"

    override val capabilities: CloudCapabilities = CloudCapabilities(
        canBrowse = true,
        canSearch = true,
        canUpload = true,
        canDownload = true,
        canDelete = true,
        canRename = true,
        canMove = true,
        canCreateFolder = true,
        supportsThumbnails = true,
        supportsStorageUsage = true,
        supportsBackgroundSync = true
    )

    private suspend fun getValidAccessToken(): String? {
        val tokenData = tokenStorage.getTokens(providerId) ?: return null
        if (tokenData.isExpired && !tokenData.refreshToken.isNullOrEmpty() && !tokenData.clientId.isNullOrEmpty()) {
            val refreshResult = oAuthManager.refreshToken(
                providerId = providerId,
                clientId = tokenData.clientId,
                refreshToken = tokenData.refreshToken
            )
            if (refreshResult.isSuccess) {
                val resp = refreshResult.getOrThrow()
                val expiresAt = System.currentTimeMillis() + (resp.expiresInSeconds * 1000L)
                tokenStorage.saveTokens(
                    providerId = providerId,
                    accessToken = resp.accessToken,
                    refreshToken = resp.refreshToken,
                    expiresAt = expiresAt,
                    accountName = tokenData.accountName,
                    accountEmail = tokenData.accountEmail,
                    clientId = tokenData.clientId
                )
                return resp.accessToken
            }
        }
        return tokenData.accessToken
    }

    override suspend fun getConnectionState(): CloudConnectionState {
        val tokens = tokenStorage.getTokens(providerId) ?: return CloudConnectionState.NotConnected
        return if (tokens.isConnected) {
            CloudConnectionState.Connected(
                accountName = tokens.accountName ?: "OneDrive User",
                accountEmail = tokens.accountEmail,
                serviceInfo = "Microsoft Graph API"
            )
        } else {
            CloudConnectionState.NotConnected
        }
    }

    override suspend fun connect(params: Map<String, String>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val accessToken = params["accessToken"] ?: ""
            val refreshToken = params["refreshToken"]
            val expiresIn = params["expiresIn"]?.toLongOrNull() ?: 3600L
            val clientId = params["clientId"]
            var accountEmail = params["email"]
            var accountName = params["name"]

            if (accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Access token cannot be empty"))
            }

            // Verify with Microsoft Graph me endpoint
            val meRequest = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me")
                .header("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(meRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("OneDrive authorization failed (${response.code}): $err"))
                }
                val meJson = JSONObject(response.body?.string().orEmpty())
                if (accountName.isNullOrEmpty()) {
                    accountName = meJson.optString("displayName").ifEmpty { "Microsoft User" }
                }
                if (accountEmail.isNullOrEmpty()) {
                    accountEmail = meJson.optString("mail").ifEmpty { meJson.optString("userPrincipalName") }
                }
            }

            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
            tokenStorage.saveTokens(
                providerId = providerId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                accountName = accountName,
                accountEmail = accountEmail,
                clientId = clientId
            )

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tokenStorage.clearTokens(providerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listMedia(
        folderId: String?,
        continuationToken: String?,
        pageSize: Int
    ): Result<MultiCloudMediaPage> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val endpoint = if (!continuationToken.isNullOrEmpty() && continuationToken.startsWith("http")) {
                continuationToken
            } else if (!folderId.isNullOrEmpty()) {
                "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children?\$top=${pageSize.coerceIn(1, 100)}"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/root/children?\$top=${pageSize.coerceIn(1, 100)}"
            }

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to list OneDrive items (${response.code}): ${response.message}"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val valueArray = json.optJSONArray("value") ?: JSONArray()
                val nextLink = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }

                val items = mutableListOf<CloudMediaItem>()
                val folders = mutableListOf<String>()

                for (i in 0 until valueArray.length()) {
                    val obj = valueArray.getJSONObject(i)
                    val folderName = obj.optString("name")
                    val objId = obj.optString("id")
                    if (obj.has("folder")) {
                        folders.add(folderName)
                        items.add(
                            CloudMediaItem(
                                providerId = providerId,
                                providerName = displayName,
                                remoteId = objId,
                                name = folderName,
                                mimeType = "application/vnd.microsoft.folder",
                                size = 0L,
                                isFolder = true,
                                folderPath = "/$folderName",
                                parentId = folderId,
                                capabilities = capabilities,
                                fileType = CloudFileType.OTHER
                            )
                        )
                    } else {
                        parseDriveItem(obj)?.let { items.add(it) }
                    }
                }

                Result.success(
                    MultiCloudMediaPage(
                        items = items,
                        folders = folders,
                        nextContinuationToken = nextLink,
                        isTruncated = nextLink != null
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMedia(request: CloudSearchRequest): Result<List<CloudMediaItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val query = request.query.orEmpty()
            val url = "https://graph.microsoft.com/v1.0/me/drive/root/search(q='$query')"

            val requestHttp = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(requestHttp).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("OneDrive search failed (${response.code})"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val valueArray = json.optJSONArray("value") ?: JSONArray()

                val items = mutableListOf<CloudMediaItem>()
                for (i in 0 until valueArray.length()) {
                    val obj = valueArray.getJSONObject(i)
                    if (!obj.has("folder")) {
                        parseDriveItem(obj)?.let { item ->
                            val matchesType = when (request.mediaType?.lowercase()) {
                                "video", "videos" -> item.isVideo
                                "photo", "photos", "image", "images" -> !item.isVideo
                                else -> true
                            }
                            val matchesSize = request.minSizeBytes == null || item.size >= request.minSizeBytes
                            if (matchesType && matchesSize) {
                                items.add(item)
                            }
                        }
                    }
                }
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun download(
        remoteId: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val downloadRequest = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/items/$remoteId/content")
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download file from OneDrive (${response.code})"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty OneDrive file response"))
                val totalBytes = body.contentLength()

                destinationFile.parentFile?.mkdirs()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upload(
        file: File,
        mimeType: String,
        targetFolder: String?,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<CloudMediaItem> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val parentPath = if (!targetFolder.isNullOrEmpty()) "items/$targetFolder:" else "root:"
            val uploadUrl = "https://graph.microsoft.com/v1.0/me/drive/$parentPath/${file.name}:/content"

            val request = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $token")
                .put(file.asRequestBody(mimeType.toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Failed to upload file to OneDrive (${response.code}): $err"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val item = parseDriveItem(json)
                if (item != null) {
                    Result.success(item)
                } else {
                    Result.success(
                        CloudMediaItem(
                            providerId = providerId,
                            providerName = displayName,
                            remoteId = json.optString("id", file.name),
                            name = file.name,
                            mimeType = mimeType,
                            size = file.length(),
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            isVideo = mimeType.startsWith("video/"),
                            capabilities = capabilities
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(remoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val request = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/items/$remoteId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 204) {
                    return@withContext Result.failure(Exception("Failed to delete OneDrive item (${response.code})"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refresh(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getStorageUsage(): Result<CloudStorageQuota?> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Microsoft OneDrive is not connected"))

            val request = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive")
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch OneDrive quota (${response.code})"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val quota = json.optJSONObject("quota")

                val total = quota?.optLong("total")
                val used = quota?.optLong("used")

                Result.success(
                    CloudStorageQuota(
                        providerId = providerId,
                        providerName = displayName,
                        usedBytes = used,
                        totalBytes = total,
                        isAvailable = quota != null,
                        statusMessage = quota?.optString("state")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDriveItem(json: JSONObject): CloudMediaItem? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val name = json.optString("name").ifEmpty { "File" }
        val size = json.optLong("size", 0L)
        val fileObj = json.optJSONObject("file")
        val mimeType = fileObj?.optString("mimeType") ?: "image/jpeg"
        val isVideo = json.has("video") || mimeType.startsWith("video/")
        val downloadUrl = json.optString("@microsoft.graph.downloadUrl").takeIf { it.isNotEmpty() }

        val createdTimeStr = json.optString("createdDateTime")
        val modifiedTimeStr = json.optString("lastModifiedDateTime")
        val createdAt = parseIsoDate(createdTimeStr)
        val modifiedAt = parseIsoDate(modifiedTimeStr)

        val thumbnails = json.optJSONArray("thumbnails")
        val firstThumb = thumbnails?.optJSONObject(0)
        val thumbUrl = firstThumb?.optJSONObject("medium")?.optString("url")
            ?: firstThumb?.optJSONObject("small")?.optString("url")
            ?: downloadUrl

        return CloudMediaItem(
            providerId = providerId,
            providerName = displayName,
            remoteId = id,
            name = name,
            mimeType = mimeType,
            size = size,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            thumbnailUrl = thumbUrl,
            downloadUrl = downloadUrl,
            isVideo = isVideo,
            capabilities = capabilities
        )
    }

    private fun parseIsoDate(isoString: String?): Long {
        if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
