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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GoogleDriveProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : CloudProvider {

    override val providerId: String = "google_drive"
    override val displayName: String = "Google Drive"

    override val capabilities: CloudCapabilities = CloudCapabilities(
        canBrowse = true,
        canSearch = true,
        canUpload = true,
        canDownload = true,
        canDelete = true,
        canRename = true,
        canMove = false,
        canCreateFolder = false,
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
                accountName = tokens.accountName ?: "Google Drive User",
                accountEmail = tokens.accountEmail,
                serviceInfo = "Google Drive API v3"
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

            // Verify the token by calling Google Drive About API
            val testRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                .header("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(testRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Google Drive authentication failed (${response.code}): $err"))
                }
                val aboutJson = JSONObject(response.body?.string().orEmpty())
                val userObj = aboutJson.optJSONObject("user")
                if (userObj != null) {
                    if (accountName.isNullOrEmpty()) accountName = userObj.optString("displayName", "Google Drive User")
                    if (accountEmail.isNullOrEmpty()) accountEmail = userObj.optString("emailAddress").takeIf { it.isNotEmpty() }
                }
            }

            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
            tokenStorage.saveTokens(
                providerId = providerId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                accountName = accountName ?: "Google Drive Account",
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
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val parentFilter = if (folderId.isNullOrEmpty() || folderId == "root") {
                "'root' in parents and trashed = false"
            } else {
                "'$folderId' in parents and trashed = false"
            }
            val urlBuilder = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("www.googleapis.com")
                .addPathSegments("drive/v3/files")
                .addQueryParameter("q", parentFilter)
                .addQueryParameter("pageSize", pageSize.toString())
                .addQueryParameter("orderBy", "folder,modifiedTime desc")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,webContentLink)")

            if (!continuationToken.isNullOrEmpty()) {
                urlBuilder.addQueryParameter("pageToken", continuationToken)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Failed to list Google Drive files (${response.code}): $err"))
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("files") ?: JSONArray()
                val nextToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

                val items = mutableListOf<CloudMediaItem>()
                val folders = mutableListOf<String>()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    val id = fileObj.getString("id")
                    val name = fileObj.getString("name")
                    val mime = fileObj.optString("mimeType", "application/octet-stream")
                    val isFolder = mime == "application/vnd.google-apps.folder"
                    val size = fileObj.optLong("size", 0L)
                    val modTimeStr = fileObj.optString("modifiedTime")
                    val modTime = try {
                        dateFormat.parse(modTimeStr)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    val fileType = if (isFolder) CloudFileType.OTHER else com.example.domain.model.multicloud.CloudFileTypeResolver.resolve(mime, name)
                    val isVideo = fileType == com.example.domain.model.multicloud.CloudFileType.VIDEO
                    val thumb = if (fileType == com.example.domain.model.multicloud.CloudFileType.IMAGE || isVideo) {
                        fileObj.optString("thumbnailLink").takeIf { it.isNotEmpty() }
                    } else null

                    if (isFolder) {
                        folders.add(name)
                    }

                    items.add(
                        CloudMediaItem(
                            providerId = providerId,
                            providerName = displayName,
                            remoteId = id,
                            name = name,
                            mimeType = mime,
                            size = size,
                            createdAt = modTime,
                            modifiedAt = modTime,
                            thumbnailUrl = thumb,
                            downloadUrl = if (isFolder) null else "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                            isVideo = isVideo,
                            isFolder = isFolder,
                            folderPath = "/$name",
                            parentId = folderId,
                            capabilities = capabilities,
                            fileType = fileType
                        )
                    )
                }

                Result.success(
                    MultiCloudMediaPage(
                        items = items,
                        folders = folders,
                        nextContinuationToken = nextToken,
                        isTruncated = nextToken != null
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
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val escapedQuery = request.query?.replace("'", "\\'") ?: ""
            val typeFilter = when (request.mediaType?.lowercase()) {
                "photo", "photos", "image", "images" -> " and mimeType contains 'image/'"
                "video", "videos" -> " and mimeType contains 'video/'"
                "audio" -> " and mimeType contains 'audio/'"
                "document", "documents" -> " and (mimeType contains 'pdf' or mimeType contains 'document' or mimeType contains 'text')"
                else -> ""
            }
            val query = "name contains '$escapedQuery' and trashed = false$typeFilter"
            val urlBuilder = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("www.googleapis.com")
                .addPathSegments("drive/v3/files")
                .addQueryParameter("q", query)
                .addQueryParameter("pageSize", "50")
                .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime,thumbnailLink)")

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Drive search failed: ${response.code}"))
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("files") ?: JSONArray()
                val items = mutableListOf<CloudMediaItem>()
                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    val id = fileObj.getString("id")
                    val name = fileObj.getString("name")
                    val mime = fileObj.optString("mimeType", "image/jpeg")
                    val size = fileObj.optLong("size", 0L)
                    val thumb = fileObj.optString("thumbnailLink").takeIf { it.isNotEmpty() }

                    items.add(
                        CloudMediaItem(
                            providerId = providerId,
                            providerName = displayName,
                            remoteId = id,
                            name = name,
                            mimeType = mime,
                            size = size,
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            thumbnailUrl = thumb,
                            downloadUrl = "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                            isVideo = mime.startsWith("video/"),
                            folderPath = "/$name",
                            capabilities = capabilities
                        )
                    )
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
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$remoteId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download file from Google Drive (${response.code})"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty body from Google Drive"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                destinationFile.parentFile?.mkdirs()
                FileOutputStream(destinationFile).use { fos ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
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
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            // Create metadata
            val metadata = JSONObject().apply {
                put("name", file.name)
                put("mimeType", mimeType)
            }

            val mediaType = mimeType.toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mediaType)
                )
                .build()

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,mimeType")
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Upload to Google Drive failed (${response.code}): $err"))
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val id = json.getString("id")
                val name = json.optString("name", file.name)
                val size = json.optLong("size", file.length())

                Result.success(
                    CloudMediaItem(
                        providerId = providerId,
                        providerName = displayName,
                        remoteId = id,
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        createdAt = System.currentTimeMillis(),
                        modifiedAt = System.currentTimeMillis(),
                        thumbnailUrl = null,
                        downloadUrl = "https://www.googleapis.com/drive/v3/files/$id?alt=media",
                        isVideo = mimeType.startsWith("video/"),
                        folderPath = "/$name",
                        capabilities = capabilities
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(remoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$remoteId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful && response.code != 204 && response.code != 404) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Failed to delete Google Drive item: $err"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
            if (token != null) Result.success(Unit)
            else Result.failure(IllegalStateException("Not authenticated"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStorageUsage(): Result<CloudStorageQuota?> = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext Result.success(
                    CloudStorageQuota(
                        providerId = providerId,
                        providerName = displayName,
                        isAvailable = false,
                        statusMessage = "Google Drive is not connected."
                    )
                )

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success(
                        CloudStorageQuota(
                            providerId = providerId,
                            providerName = displayName,
                            isAvailable = false,
                            statusMessage = "Unable to fetch Google Drive storage usage."
                        )
                    )
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val quotaObj = json.optJSONObject("storageQuota")
                if (quotaObj != null) {
                    val limit = quotaObj.optLong("limit", -1L).takeIf { it > 0 }
                    val usage = quotaObj.optLong("usage", 0L)
                    val usageInDrive = quotaObj.optLong("usageInDrive", 0L)

                    Result.success(
                        CloudStorageQuota(
                            providerId = providerId,
                            providerName = displayName,
                            usedBytes = usage,
                            totalBytes = limit,
                            isAvailable = true,
                            statusMessage = "Drive usage: ${usageInDrive / (1024 * 1024)} MB"
                        )
                    )
                } else {
                    Result.success(
                        CloudStorageQuota(
                            providerId = providerId,
                            providerName = displayName,
                            isAvailable = false,
                            statusMessage = "Storage quota info not returned by Google Drive."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.success(
                CloudStorageQuota(
                    providerId = providerId,
                    providerName = displayName,
                    isAvailable = false,
                    statusMessage = "Google Drive storage quota unavailable offline."
                )
            )
        }
    }
}
