package com.example.data.multicloud

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.model.multicloud.CloudOperation
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GooglePhotosProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : CloudProvider {

    override val providerId: String = "google_photos"
    override val displayName: String = "Google Photos"

    override val capabilities: CloudCapabilities = CloudCapabilities(
        canBrowse = true,
        canSearch = true,
        canUpload = true,
        canDownload = true,
        canDelete = false, // Google Photos API policy restriction
        canRename = false,
        canMove = false,
        canCreateFolder = false,
        supportsThumbnails = true,
        supportsStorageUsage = false, // Google Photos API does not expose quota
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
                accountName = tokens.accountName ?: "Google Photos User",
                accountEmail = tokens.accountEmail,
                serviceInfo = "Google Photos Library API"
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
            val accountEmail = params["email"]
            val accountName = params["name"]

            if (accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Access token cannot be empty"))
            }

            // Verify the token by calling Google Photos API mediaItems list with pageSize=1
            val testRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=1")
                .header("Authorization", "Bearer $accessToken")
                .build()

            httpClient.newCall(testRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Google Photos authentication failed (${response.code}): $err"))
                }
            }

            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
            tokenStorage.saveTokens(
                providerId = providerId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                accountName = accountName ?: "Google Account",
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
                ?: return@withContext Result.failure(IllegalStateException("Google Photos is not connected"))

            val urlBuilder = StringBuilder("https://photoslibrary.googleapis.com/v1/mediaItems?")
            urlBuilder.append("pageSize=").append(pageSize.coerceIn(1, 100))
            if (!continuationToken.isNullOrEmpty()) {
                urlBuilder.append("&pageToken=").append(continuationToken)
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to list Google Photos (${response.code}): ${response.message}"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val itemsArray = json.optJSONArray("mediaItems") ?: JSONArray()
                val nextToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

                val items = mutableListOf<CloudMediaItem>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    parseMediaItem(obj)?.let { items.add(it) }
                }

                Result.success(
                    MultiCloudMediaPage(
                        items = items,
                        folders = emptyList(), // Google Photos API does not expose arbitrary folder trees
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
                ?: return@withContext Result.failure(IllegalStateException("Google Photos is not connected"))

            val searchBody = JSONObject()
            searchBody.put("pageSize", 50)

            val filters = JSONObject()
            val mediaTypeFilter = JSONObject()
            val mediaTypes = JSONArray()

            when (request.mediaType?.lowercase()) {
                "video", "videos" -> mediaTypes.put("VIDEO")
                "photo", "photos", "image", "images" -> mediaTypes.put("PHOTO")
                else -> mediaTypes.put("ALL_MEDIA")
            }
            mediaTypeFilter.put("mediaTypes", mediaTypes)
            filters.put("mediaTypeFilter", mediaTypeFilter)
            searchBody.put("filters", filters)

            val requestHttp = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems:search")
                .header("Authorization", "Bearer $token")
                .post(searchBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requestHttp).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Google Photos search failed (${response.code})"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val itemsArray = json.optJSONArray("mediaItems") ?: JSONArray()

                val items = mutableListOf<CloudMediaItem>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    parseMediaItem(obj)?.let { item ->
                        val matchesQuery = request.query.isNullOrEmpty() ||
                                item.name.contains(request.query, ignoreCase = true)
                        if (matchesQuery) {
                            items.add(item)
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
                ?: return@withContext Result.failure(IllegalStateException("Google Photos is not connected"))

            // 1. Fetch item details to get fresh baseUrl
            val getRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems/$remoteId")
                .header("Authorization", "Bearer $token")
                .build()

            var baseUrl = ""
            var isVideo = false
            httpClient.newCall(getRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to get Google Photos item metadata (${resp.code})"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                baseUrl = json.optString("baseUrl")
                isVideo = json.optJSONObject("mediaMetadata")?.has("video") == true
            }

            if (baseUrl.isEmpty()) {
                return@withContext Result.failure(Exception("Invalid download URL from Google Photos"))
            }

            val downloadUrl = if (isVideo) "$baseUrl=dv" else "$baseUrl=d"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(downloadRequest).execute().use { dlResp ->
                if (!dlResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to stream Google Photos bytes (${dlResp.code})"))
                }

                val body = dlResp.body ?: return@withContext Result.failure(Exception("Empty download response body"))
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
                ?: return@withContext Result.failure(IllegalStateException("Google Photos is not connected"))

            // Step 1: Upload raw bytes to uploads endpoint
            val uploadRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/uploads")
                .header("Authorization", "Bearer $token")
                .header("Content-type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "raw")
                .post(file.asRequestBody("application/octet-stream".toMediaType()))
                .build()

            val uploadToken: String
            httpClient.newCall(uploadRequest).execute().use { upResp ->
                if (!upResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Google Photos upload failed (${upResp.code}): ${upResp.message}"))
                }
                uploadToken = upResp.body?.string().orEmpty()
            }

            if (uploadToken.isEmpty()) {
                return@withContext Result.failure(Exception("Received empty upload token from Google Photos"))
            }

            // Step 2: BatchCreate media item
            val createJson = JSONObject()
            val newItems = JSONArray()
            val singleItem = JSONObject()
            singleItem.put("description", file.name)
            val simpleMediaItem = JSONObject()
            simpleMediaItem.put("fileName", file.name)
            simpleMediaItem.put("uploadToken", uploadToken)
            singleItem.put("simpleMediaItem", simpleMediaItem)
            newItems.put(singleItem)
            createJson.put("newMediaItems", newItems)

            val batchRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems.batchCreate")
                .header("Authorization", "Bearer $token")
                .post(createJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(batchRequest).execute().use { batchResp ->
                if (!batchResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Google Photos batchCreate failed (${batchResp.code})"))
                }
                val batchBody = JSONObject(batchResp.body?.string().orEmpty())
                val results = batchBody.optJSONArray("newMediaItemResults")
                val firstResult = results?.optJSONObject(0)
                val status = firstResult?.optJSONObject("status")
                val message = status?.optString("message")

                if (status != null && status.optInt("code", 0) != 0) {
                    return@withContext Result.failure(Exception("Google Photos rejected upload: $message"))
                }

                val createdMedia = firstResult?.optJSONObject("mediaItem")
                if (createdMedia != null) {
                    val parsed = parseMediaItem(createdMedia)
                    if (parsed != null) {
                        return@withContext Result.success(parsed)
                    }
                }
            }

            Result.success(
                CloudMediaItem(
                    providerId = providerId,
                    providerName = displayName,
                    remoteId = uploadToken,
                    name = file.name,
                    mimeType = mimeType,
                    size = file.length(),
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    isVideo = mimeType.startsWith("video/"),
                    capabilities = capabilities
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(remoteId: String): Result<Unit> {
        // Enforce official limitation: Google Photos API does not permit third-party deletion
        return Result.failure(
            UnsupportedOperationException(
                "Google Photos currently does not allow file deletion through the official API."
            )
        )
    }

    override suspend fun refresh(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getStorageUsage(): Result<CloudStorageQuota?> {
        // Enforce official limitation: Google Photos Library API does not expose storage quota
        return Result.success(
            CloudStorageQuota(
                providerId = providerId,
                providerName = displayName,
                usedBytes = null,
                totalBytes = null,
                isAvailable = false,
                statusMessage = "Storage usage unavailable via Google Photos API"
            )
        )
    }

    private fun parseMediaItem(json: JSONObject): CloudMediaItem? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val filename = json.optString("filename").ifEmpty { "Photo" }
        val baseUrl = json.optString("baseUrl")
        val mimeType = json.optString("mimeType").ifEmpty { "image/jpeg" }
        val metadata = json.optJSONObject("mediaMetadata")
        val isVideo = metadata?.has("video") == true || mimeType.startsWith("video/")
        val width = metadata?.optString("width")?.toIntOrNull() ?: 0
        val height = metadata?.optString("height")?.toIntOrNull() ?: 0

        val creationTimeStr = metadata?.optString("creationTime")
        val createdAt = parseIsoDate(creationTimeStr)

        val thumbUrl = if (baseUrl.isNotEmpty()) "$baseUrl=w400-h400" else null
        val dlUrl = if (baseUrl.isNotEmpty()) (if (isVideo) "$baseUrl=dv" else "$baseUrl=d") else null

        return CloudMediaItem(
            providerId = providerId,
            providerName = displayName,
            remoteId = id,
            name = filename,
            mimeType = mimeType,
            size = 0L, // Google Photos API omits file size in list responses
            createdAt = createdAt,
            modifiedAt = createdAt,
            thumbnailUrl = thumbUrl,
            downloadUrl = dlUrl,
            isVideo = isVideo,
            width = width,
            height = height,
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
