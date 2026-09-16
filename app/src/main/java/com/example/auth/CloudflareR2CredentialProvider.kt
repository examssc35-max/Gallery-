package com.example.auth

import com.example.data.local.R2Credentials
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloudflareR2CredentialProvider(
    private val r2Repository: R2Repository
) {
    companion object {
        const val PROVIDER_ID = "r2"
        const val DISPLAY_NAME = "Cloudflare R2"
    }

    suspend fun saveAndVerify(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String,
        endpoint: String
    ): Result<CloudConnectionState.Connected> = withContext(Dispatchers.IO) {
        try {
            val creds = R2Credentials(
                accountId = accountId.trim(),
                accessKeyId = accessKeyId.trim(),
                secretAccessKey = secretAccessKey.trim(),
                bucketName = bucketName.trim(),
                endpoint = endpoint.trim().ifEmpty {
                    if (accountId.isNotEmpty()) "https://${accountId.trim()}.r2.cloudflarestorage.com" else ""
                }
            )

            val testRes = r2Repository.testConnection(creds)
            if (testRes.isFailure) {
                val err = testRes.exceptionOrNull()?.message ?: "Failed to connect to Cloudflare R2 bucket"
                return@withContext Result.failure(Exception(err))
            }

            r2Repository.saveCredentials(creds)

            Result.success(
                CloudConnectionState.Connected(
                    accountName = creds.bucketName,
                    accountEmail = null,
                    serviceInfo = "Cloudflare R2 (S3 API)"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyConnection(): Result<CloudConnectionState> = withContext(Dispatchers.IO) {
        val creds = r2Repository.getCredentials()
        if (creds.accessKeyId.isEmpty() || creds.secretAccessKey.isEmpty() || creds.bucketName.isEmpty()) {
            return@withContext Result.success(CloudConnectionState.NotConnected)
        }

        val testRes = r2Repository.testConnection()
        if (testRes.isSuccess) {
            Result.success(
                CloudConnectionState.Connected(
                    accountName = creds.bucketName,
                    accountEmail = null,
                    serviceInfo = "Cloudflare R2 (S3 API)"
                )
            )
        } else {
            Result.success(CloudConnectionState.Error(testRes.exceptionOrNull()?.message ?: "R2 connection error"))
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            r2Repository.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
