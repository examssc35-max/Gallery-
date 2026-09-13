package com.example.data.cloudflare

import android.util.Xml
import com.example.data.local.R2Credentials
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaClassifier
import com.example.domain.model.R2Item
import com.example.domain.model.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class R2ListResult(
    val items: List<R2Item>,
    val nextContinuationToken: String? = null,
    val isTruncated: Boolean = false
)

class R2Client(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    fun getPresignedUrl(
        credentials: R2Credentials,
        key: String,
        expiresSeconds: Long = 86400
    ): String {
        val (host, _) = getHostAndBaseUrl(credentials)
        val bucket = credentials.bucketName.trim()
        val cleanKey = key.trimStart('/')
        val path = "/$bucket/$cleanKey"
        return AwsSigV4Signer.generatePresignedGetUrl(
            host = host,
            path = path,
            accessKeyId = credentials.accessKeyId.trim(),
            secretAccessKey = credentials.secretAccessKey.trim(),
            expiresSeconds = expiresSeconds
        )
    }

    private fun getHostAndBaseUrl(credentials: R2Credentials): Pair<String, String> {
        val endpoint = credentials.endpoint.trim()
        val accountId = credentials.accountId.trim()
        val fullUrl = if (endpoint.isNotEmpty()) {
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                "https://$endpoint"
            } else endpoint
        } else {
            "https://$accountId.r2.cloudflarestorage.com"
        }
        val uri = URI(fullUrl)
        val host = uri.host ?: "$accountId.r2.cloudflarestorage.com"
        return Pair(host, fullUrl.trimEnd('/'))
    }

    suspend fun testConnection(credentials: R2Credentials): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (credentials.accountId.isBlank() && credentials.endpoint.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Account ID is required"))
            }
            if (credentials.accessKeyId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Access Key ID is required"))
            }
            if (credentials.secretAccessKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Secret Access Key is required"))
            }
            if (credentials.bucketName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Bucket Name is required"))
            }

            val (host, baseUrl) = getHostAndBaseUrl(credentials)
            val bucket = credentials.bucketName.trim()
            val path = "/$bucket"
            val queryParams = mapOf("list-type" to "2", "max-keys" to "1")

            val signResult = AwsSigV4Signer.sign(
                method = "GET",
                host = host,
                path = path,
                queryParams = queryParams,
                headers = emptyMap(),
                payloadHash = AwsSigV4Signer.emptyPayloadHash(),
                accessKeyId = credentials.accessKeyId.trim(),
                secretAccessKey = credentials.secretAccessKey.trim()
            )

            val url = "$baseUrl$path?list-type=2&max-keys=1"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("host", host)
                .header("x-amz-date", signResult.amzDate)
                .header("x-amz-content-sha256", signResult.payloadHash)
                .header("Authorization", signResult.authorization)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Result.success(true)
                } else {
                    val code = resp.code
                    val errorBody = resp.body?.string() ?: ""
                    val message = when (code) {
                        403 -> "Access Denied: Invalid Access Key or Secret Key"
                        404 -> "Bucket '$bucket' not found"
                        else -> "Connection failed with HTTP $code: ${parseErrorMessage(errorBody)}"
                    }
                    Result.failure(Exception(message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listObjectsPage(
        credentials: R2Credentials,
        prefix: String = "",
        delimiter: String = "/",
        maxKeys: Int = 100,
        continuationToken: String? = null
    ): Result<R2ListResult> = withContext(Dispatchers.IO) {
        try {
            val (host, baseUrl) = getHostAndBaseUrl(credentials)
            val bucket = credentials.bucketName.trim()
            val path = "/$bucket"
            val queryParams = mutableMapOf("list-type" to "2")
            if (prefix.isNotEmpty()) {
                queryParams["prefix"] = prefix
            }
            if (delimiter.isNotEmpty()) {
                queryParams["delimiter"] = delimiter
            }
            queryParams["max-keys"] = maxKeys.toString()
            if (!continuationToken.isNullOrBlank()) {
                queryParams["continuation-token"] = continuationToken
            }

            val signResult = AwsSigV4Signer.sign(
                method = "GET",
                host = host,
                path = path,
                queryParams = queryParams,
                headers = emptyMap(),
                payloadHash = AwsSigV4Signer.emptyPayloadHash(),
                accessKeyId = credentials.accessKeyId.trim(),
                secretAccessKey = credentials.secretAccessKey.trim()
            )

            val queryStr = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            val url = "$baseUrl$path?$queryStr"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("host", host)
                .header("x-amz-date", signResult.amzDate)
                .header("x-amz-content-sha256", signResult.payloadHash)
                .header("Authorization", signResult.authorization)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: ""
                    return@withContext Result.failure(Exception("List failed: ${parseErrorMessage(errorBody)}"))
                }
                val body = resp.body?.string() ?: ""
                val listResult = parseListObjectsXml(body, prefix)
                Result.success(listResult)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listObjects(
        credentials: R2Credentials,
        prefix: String = "",
        delimiter: String = "/"
    ): Result<List<R2Item>> = listObjectsPage(credentials, prefix, delimiter, 1000).map { it.items }

    suspend fun fetchStorageUsage(
        credentials: R2Credentials,
        onProgress: (scannedObjects: Int, scannedBytes: Long) -> Unit = { _, _ -> }
    ): Result<StorageUsage> = withContext(Dispatchers.IO) {
        try {
            if (credentials.bucketName.isBlank() || credentials.secretAccessKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Cloudflare R2 is not connected"))
            }

            var totalBytes = 0L
            var photoBytes = 0L
            var videoBytes = 0L
            var otherBytes = 0L
            var photoCount = 0
            var videoCount = 0
            var otherCount = 0
            var totalObjects = 0

            var continuationToken: String? = null

            do {
                // Flat listing: delimiter = "" means ALL objects in bucket across all folders/prefixes are listed without folding into CommonPrefixes.
                // maxKeys = 1000 retrieves up to 1000 object headers per S3 call without downloading any file bodies.
                val pageResult = listObjectsPage(
                    credentials = credentials,
                    prefix = "",
                    delimiter = "",
                    maxKeys = 1000,
                    continuationToken = continuationToken
                )

                if (pageResult.isFailure) {
                    val error = pageResult.exceptionOrNull() ?: Exception("Failed to list objects for storage calculation")
                    return@withContext Result.failure(error)
                }

                val page = pageResult.getOrThrow()
                for (item in page.items) {
                    // S3 folder markers (keys ending with '/' and 0 bytes) are directories, not files
                    if (item.isFolder || (item.key.endsWith("/") && item.size == 0L)) {
                        continue
                    }
                    val size = item.size.coerceAtLeast(0L)
                    val category = MediaClassifier.classify(item.key, item.mimeType)
                    when (category) {
                        MediaCategory.PHOTO -> {
                            photoBytes += size
                            photoCount++
                        }
                        MediaCategory.VIDEO -> {
                            videoBytes += size
                            videoCount++
                        }
                        MediaCategory.OTHER -> {
                            otherBytes += size
                            otherCount++
                        }
                    }
                    totalBytes += size
                    totalObjects++
                }

                onProgress(totalObjects, totalBytes)
                continuationToken = if (page.isTruncated) page.nextContinuationToken else null
            } while (!continuationToken.isNullOrBlank())

            val result = StorageUsage(
                totalBytes = totalBytes,
                photoBytes = photoBytes,
                videoBytes = videoBytes,
                otherBytes = otherBytes,
                photoCount = photoCount,
                videoCount = videoCount,
                otherCount = otherCount,
                totalObjectCount = totalObjects,
                lastUpdated = System.currentTimeMillis(),
                isCached = false
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadStream(
        credentials: R2Credentials,
        key: String,
        inputStream: InputStream,
        contentLength: Long,
        mimeType: String = "application/octet-stream",
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (host, baseUrl) = getHostAndBaseUrl(credentials)
            val bucket = credentials.bucketName.trim()
            val cleanKey = key.trimStart('/')
            val path = "/$bucket/$cleanKey"

            val headers = mapOf("content-type" to mimeType)
            val payloadHash = "UNSIGNED-PAYLOAD"

            val signResult = AwsSigV4Signer.sign(
                method = "PUT",
                host = host,
                path = path,
                queryParams = emptyMap(),
                headers = headers,
                payloadHash = payloadHash,
                accessKeyId = credentials.accessKeyId.trim(),
                secretAccessKey = credentials.secretAccessKey.trim()
            )

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()

                override fun contentLength() = contentLength

                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(8192)
                    var uploaded = 0L
                    inputStream.use { stream ->
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            onProgress(uploaded, contentLength)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url("$baseUrl$path")
                .put(requestBody)
                .header("host", host)
                .header("x-amz-date", signResult.amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", signResult.authorization)
                .header("Content-Type", mimeType)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = resp.body?.string() ?: ""
                    Result.failure(Exception("Upload failed ($resp.code): ${parseErrorMessage(errorBody)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadToFile(
        credentials: R2Credentials,
        key: String,
        destinationFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val (host, baseUrl) = getHostAndBaseUrl(credentials)
            val bucket = credentials.bucketName.trim()
            val cleanKey = key.trimStart('/')
            val path = "/$bucket/$cleanKey"

            val signResult = AwsSigV4Signer.sign(
                method = "GET",
                host = host,
                path = path,
                queryParams = emptyMap(),
                headers = emptyMap(),
                payloadHash = AwsSigV4Signer.emptyPayloadHash(),
                accessKeyId = credentials.accessKeyId.trim(),
                secretAccessKey = credentials.secretAccessKey.trim()
            )

            val request = Request.Builder()
                .url("$baseUrl$path")
                .get()
                .header("host", host)
                .header("x-amz-date", signResult.amzDate)
                .header("x-amz-content-sha256", signResult.payloadHash)
                .header("Authorization", signResult.authorization)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Download failed: ${parseErrorMessage(errorBody)}"))
                }

                val body = resp.body ?: return@withContext Result.failure(Exception("Empty body"))
                val totalLength = body.contentLength()

                destinationFile.parentFile?.mkdirs()
                FileOutputStream(destinationFile).use { fileOut ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            fileOut.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalLength)
                        }
                    }
                }
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteObject(
        credentials: R2Credentials,
        key: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (host, baseUrl) = getHostAndBaseUrl(credentials)
            val bucket = credentials.bucketName.trim()
            val cleanKey = key.trimStart('/')
            val path = "/$bucket/$cleanKey"

            val signResult = AwsSigV4Signer.sign(
                method = "DELETE",
                host = host,
                path = path,
                queryParams = emptyMap(),
                headers = emptyMap(),
                payloadHash = AwsSigV4Signer.emptyPayloadHash(),
                accessKeyId = credentials.accessKeyId.trim(),
                secretAccessKey = credentials.secretAccessKey.trim()
            )

            val request = Request.Builder()
                .url("$baseUrl$path")
                .delete()
                .header("host", host)
                .header("x-amz-date", signResult.amzDate)
                .header("x-amz-content-sha256", signResult.payloadHash)
                .header("Authorization", signResult.authorization)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful || resp.code == 204) {
                    Result.success(Unit)
                } else {
                    val errorBody = resp.body?.string() ?: ""
                    Result.failure(Exception("Delete failed (${resp.code}): ${parseErrorMessage(errorBody)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(xml: String): String {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            var message = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("Message", ignoreCase = true)) {
                    message = parser.nextText()
                    break
                }
                eventType = parser.next()
            }
            if (message.isNotEmpty()) message else xml.take(120)
        } catch (_: Exception) {
            xml.take(120)
        }
    }

    private fun parseListObjectsXml(xml: String, currentPrefix: String): R2ListResult {
        val items = mutableListOf<R2Item>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType

        var inContents = false
        var inCommonPrefixes = false
        var isTruncated = false
        var nextContinuationToken: String? = null
        var currentKey = ""
        var currentSize = 0L
        var currentLastModified = 0L

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val isoFormatSec = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName.equals("IsTruncated", ignoreCase = true) -> {
                            isTruncated = parser.nextText().trim().toBoolean()
                        }
                        tagName.equals("NextContinuationToken", ignoreCase = true) -> {
                            nextContinuationToken = parser.nextText().trim()
                        }
                        tagName.equals("Contents", ignoreCase = true) -> {
                            inContents = true
                            currentKey = ""
                            currentSize = 0L
                            currentLastModified = 0L
                        }
                        tagName.equals("CommonPrefixes", ignoreCase = true) -> {
                            inCommonPrefixes = true
                        }
                        inContents && tagName.equals("Key", ignoreCase = true) -> {
                            currentKey = parser.nextText()
                        }
                        inContents && tagName.equals("Size", ignoreCase = true) -> {
                            currentSize = parser.nextText().toLongOrNull() ?: 0L
                        }
                        inContents && tagName.equals("LastModified", ignoreCase = true) -> {
                            val text = parser.nextText()
                            currentLastModified = try {
                                isoFormat.parse(text)?.time ?: isoFormatSec.parse(text)?.time ?: 0L
                            } catch (_: Exception) { 0L }
                        }
                        inCommonPrefixes && tagName.equals("Prefix", ignoreCase = true) -> {
                            val prefixStr = parser.nextText()
                            val name = prefixStr.removeSuffix("/").substringAfterLast('/') + "/"
                            items.add(
                                R2Item(
                                    key = prefixStr,
                                    name = name,
                                    size = 0L,
                                    lastModified = 0L,
                                    isFolder = true,
                                    mimeType = "inode/directory"
                                )
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("Contents", ignoreCase = true)) {
                        inContents = false
                        if (currentKey.isNotEmpty() && currentKey != currentPrefix) {
                            val name = currentKey.removeSuffix("/").substringAfterLast('/')
                            val isDir = currentKey.endsWith("/")
                            val mime = if (isDir) "inode/directory" else guessMimeType(name)
                            items.add(
                                R2Item(
                                    key = currentKey,
                                    name = name,
                                    size = currentSize,
                                    lastModified = currentLastModified,
                                    isFolder = isDir,
                                    mimeType = mime
                                )
                            )
                        }
                    } else if (tagName.equals("CommonPrefixes", ignoreCase = true)) {
                        inCommonPrefixes = false
                    }
                }
            }
            eventType = parser.next()
        }
        return R2ListResult(items = items, nextContinuationToken = nextContinuationToken, isTruncated = isTruncated)
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
            "dng" -> "image/x-adobe-dng"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "3gp", "3gpp" -> "video/3gpp"
            "m4v" -> "video/x-m4v"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
