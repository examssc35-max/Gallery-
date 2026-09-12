package com.example.domain.repository

import com.example.data.local.R2Credentials
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import kotlinx.coroutines.flow.Flow
import java.io.File

data class CloudMediaPage(
    val items: List<MediaItem>,
    val folders: List<String>,
    val nextContinuationToken: String? = null,
    val isTruncated: Boolean = false
)

interface R2Repository {
    val credentialsFlow: Flow<R2Credentials>
    suspend fun getCredentials(): R2Credentials
    suspend fun testConnection(customCredentials: R2Credentials? = null): Result<Boolean>
    suspend fun listObjects(prefix: String = ""): Result<List<R2Item>>
    suspend fun listCloudMediaPage(
        prefix: String = "",
        continuationToken: String? = null,
        pageSize: Int = 50
    ): Result<CloudMediaPage>
    suspend fun getPresignedUrl(key: String, expiresSeconds: Long = 86400): Result<String>
    suspend fun uploadMedia(
        item: MediaItem,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<String>
    suspend fun downloadObject(
        r2Item: R2Item,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File>
    suspend fun downloadKeyToFile(
        key: String,
        destinationFile: File,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File>
    suspend fun deleteObject(key: String): Result<Unit>
    suspend fun saveCredentials(credentials: R2Credentials)
    suspend fun disconnect()
}
