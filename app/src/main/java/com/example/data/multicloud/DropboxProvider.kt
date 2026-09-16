package com.example.data.multicloud

import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DropboxProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : CloudProvider {

    override val providerId: String = "dropbox"
    override val displayName: String = "Dropbox"

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
                accountName = tokens.accountName ?: "Dropbox User",
                accountEmail = tokens.accountEmail,
                serviceInfo = "Dropbox API v2"
            )
        } else {
            CloudConnectionState.NotConnected
        }
    }

    override suspend fun connect(params: Map<String, String>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val accessToken = params["accessToken"] ?: ""
            val refreshToken = params["refreshToken"]
            val expiresIn = params["expiresIn"]?.toLongOrNull() ?: 14400L
            val clientId = params["clientId"]
            var accountEmail = params["email"]
            var accountName = params["name"]

            if (accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Access token cannot be empty"))
            }

            // Verify with Dropbox get_current_account
            val meRequest = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $accessToken")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(meRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Dropbox authentication failed (${response.code}): $err"))
                }
                val meJson = JSONObject(response.body?.string().orEmpty())
                val nameObj = meJson.optJSONObject("name")
                if (accountName.isNullOrEmpty()) {
                    accountName = nameObj?.optString("display_name").orEmpty().ifEmpty { "Dropbox User" }
                }
                if (accountEmail.isNullOrEmpty()) {
                    accountEmail = meJson.optString("email")
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val requestBuilder: Request.Builder
            if (!continuationToken.isNullOrEmpty()) {
                val json = JSONObject().apply {
                    put("cursor", continuationToken)
                }
                requestBuilder = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder/continue")
                    .header("Authorization", "Bearer $token")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
            } else {
                val path = if (!folderId.isNullOrEmpty()) folderId else ""
                val json = JSONObject().apply {
                    put("path", path)
                    put("recursive", false)
                    put("include_media_info", true)
                    put("limit", pageSize.coerceIn(1, 100))
                }
                requestBuilder = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder")
                    .header("Authorization", "Bearer $token")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to list Dropbox folder (${response.code}): ${response.message}"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val entries = json.optJSONArray("entries") ?: JSONArray()
                val cursor = json.optString("cursor").takeIf { it.isNotEmpty() }
                val hasMore = json.optBoolean("has_more", false)

                val items = mutableListOf<CloudMediaItem>()
                val folders = mutableListOf<String>()

                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val tag = entry.optString(".tag")
                    if (tag == "folder") {
                        folders.add(entry.optString("name"))
                    } else if (tag == "file") {
                        parseDropboxItem(entry, token)?.let { items.add(it) }
                    }
                }

                Result.success(
                    MultiCloudMediaPage(
                        items = items,
                        folders = folders,
                        nextContinuationToken = if (hasMore) cursor else null,
                        isTruncated = hasMore
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val searchJson = JSONObject().apply {
                put("query", request.query.orEmpty())
                val options = JSONObject().apply {
                    put("max_results", 50)
                }
                put("options", options)
            }

            val requestHttp = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/search_v2")
                .header("Authorization", "Bearer $token")
                .post(searchJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requestHttp).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Dropbox search failed (${response.code})"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val matches = json.optJSONArray("matches") ?: JSONArray()

                val items = mutableListOf<CloudMediaItem>()
                for (i in 0 until matches.length()) {
                    val match = matches.getJSONObject(i)
                    val metadata = match.optJSONObject("metadata")?.optJSONObject("metadata")
                    if (metadata != null && metadata.optString(".tag") == "file") {
                        parseDropboxItem(metadata, token)?.let { item ->
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val argJson = JSONObject().apply {
                put("path", remoteId)
            }

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", argJson.toString())
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download from Dropbox (${response.code})"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty Dropbox download stream"))
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val remotePath = if (!targetFolder.isNullOrEmpty()) {
                val clean = targetFolder.trim('/')
                "/$clean/${file.name}"
            } else {
                "/${file.name}"
            }

            val argJson = JSONObject().apply {
                put("path", remotePath)
                put("mode", "add")
                put("autorename", true)
                put("mute", false)
            }

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", argJson.toString())
                .header("Content-Type", "application/octet-stream")
                .post(file.asRequestBody("application/octet-stream".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Failed to upload to Dropbox (${response.code}): $err"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val item = parseDropboxItem(json, token)
                if (item != null) {
                    Result.success(item)
                } else {
                    Result.success(
                        CloudMediaItem(
                            providerId = providerId,
                            providerName = displayName,
                            remoteId = json.optString("id", remotePath),
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val argJson = JSONObject().apply {
                put("path", remoteId)
            }

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .header("Authorization", "Bearer $token")
                .post(argJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to delete Dropbox item (${response.code})"))
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
                ?: return@withContext Result.failure(IllegalStateException("Dropbox is not connected"))

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_space_usage")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch Dropbox space usage (${response.code})"))
                }

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val used = json.optLong("used", 0L)
                val allocation = json.optJSONObject("allocation")
                val allocated = allocation?.optLong("allocated")

                Result.success(
                    CloudStorageQuota(
                        providerId = providerId,
                        providerName = displayName,
                        usedBytes = used,
                        totalBytes = allocated,
                        isAvailable = true,
                        statusMessage = "Dropbox Account"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchTemporaryLink(pathOrId: String, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("path", pathOrId)
            }
            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    JSONObject(body).optString("link").takeIf { it.isNotEmpty() }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDropboxItem(json: JSONObject, token: String): CloudMediaItem? {
        val id = json.optString("id").takeIf { it.isNotEmpty() }
            ?: json.optString("path_lower").takeIf { it.isNotEmpty() }
            ?: return null

        val name = json.optString("name").ifEmpty { "File" }
        val size = json.optLong("size", 0L)
        val pathLower = json.optString("path_lower")
        val clientModified = json.optString("client_modified")
        val modifiedAt = parseIsoDate(clientModified)

        val isVideo = name.endsWith(".mp4", ignoreCase = true) ||
                name.endsWith(".mov", ignoreCase = true) ||
                name.endsWith(".mkv", ignoreCase = true)
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        return CloudMediaItem(
            providerId = providerId,
            providerName = displayName,
            remoteId = id,
            name = name,
            mimeType = mimeType,
            size = size,
            createdAt = modifiedAt,
            modifiedAt = modifiedAt,
            thumbnailUrl = null, // Will load or stream as needed
            downloadUrl = null,
            isVideo = isVideo,
            folderPath = pathLower,
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
