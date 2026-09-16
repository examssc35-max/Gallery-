package com.example.auth

import android.net.Uri
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.security.OAuthManager
import com.example.security.SecureCloudTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DropboxAuthProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val PROVIDER_ID = "dropbox"
        const val DISPLAY_NAME = "Dropbox"
        const val REDIRECT_URI = "cloudgallery://oauth/dropbox"
        const val AUTH_ENDPOINT = "https://www.dropbox.com/oauth2/authorize"
        const val TOKEN_ENDPOINT = "https://api.dropboxapi.com/oauth2/token"
        const val SCOPE = "files.metadata.read files.content.read account_info.read"

        const val DEFAULT_CLIENT_ID = "dbapp-cloudgallery"
    }

    private var pendingVerifier: String? = null
    private var pendingClientId: String? = null

    fun buildAuthorizationUrl(customClientId: String? = null): String {
        val clientId = customClientId?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CLIENT_ID
        val verifier = oAuthManager.generateCodeVerifier()
        val challenge = oAuthManager.generateCodeChallenge(verifier)
        pendingVerifier = verifier
        pendingClientId = clientId

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("token_access_type", "offline")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", "state_$PROVIDER_ID")
            .build()
            .toString()
    }

    suspend fun handleCallback(
        code: String,
        customClientId: String? = null,
        verifier: String? = null
    ): Result<CloudConnectionState.Connected> = withContext(Dispatchers.IO) {
        try {
            val finalClientId = customClientId?.trim()?.takeIf { it.isNotEmpty() }
                ?: pendingClientId
                ?: DEFAULT_CLIENT_ID
            val finalVerifier = verifier ?: pendingVerifier
                ?: return@withContext Result.failure(IllegalStateException("No PKCE code verifier found for session"))

            val formBody = FormBody.Builder()
                .add("client_id", finalClientId)
                .add("code", code.trim())
                .add("code_verifier", finalVerifier)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
                .build()

            val tokenReq = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            httpClient.newCall(tokenReq).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Dropbox authorization failed (${response.code}): $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                val expiresIn = json.optLong("expires_in", 14400L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                // Verify access to Dropbox API
                var accountName = "Dropbox User"
                var accountEmail: String? = null

                val meRequest = Request.Builder()
                    .url("https://api.dropboxapi.com/2/users/get_current_account")
                    .header("Authorization", "Bearer $accessToken")
                    .post("null".toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(meRequest).execute().use { meResp ->
                    if (meResp.isSuccessful) {
                        val meJson = JSONObject(meResp.body?.string().orEmpty())
                        val nameObj = meJson.optJSONObject("name")
                        accountName = nameObj?.optString("display_name").orEmpty().ifEmpty { "Dropbox User" }
                        accountEmail = meJson.optString("email").takeIf { it.isNotEmpty() }
                    } else {
                        val err = meResp.body?.string().orEmpty()
                        return@withContext Result.failure(Exception("Dropbox account verification failed (${meResp.code}): $err"))
                    }
                }

                // Save tokens securely in Keystore-backed storage
                tokenStorage.saveTokens(
                    providerId = PROVIDER_ID,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    accountName = accountName,
                    accountEmail = accountEmail,
                    clientId = finalClientId
                )

                pendingVerifier = null
                pendingClientId = null

                Result.success(
                    CloudConnectionState.Connected(
                        accountName = accountName,
                        accountEmail = accountEmail,
                        serviceInfo = "Dropbox API v2"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyConnection(): Result<CloudConnectionState> = withContext(Dispatchers.IO) {
        val tokens = tokenStorage.getTokens(PROVIDER_ID)
            ?: return@withContext Result.success(CloudConnectionState.NotConnected)

        if (!tokens.isConnected) {
            return@withContext Result.success(CloudConnectionState.NotConnected)
        }

        try {
            val req = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .post("null".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(
                        CloudConnectionState.Connected(
                            accountName = tokens.accountName ?: "Dropbox User",
                            accountEmail = tokens.accountEmail,
                            serviceInfo = "Dropbox API v2"
                        )
                    )
                } else if (resp.code == 401 || resp.code == 403) {
                    Result.success(CloudConnectionState.AuthRequired("Session expired. Please reconnect."))
                } else {
                    Result.success(CloudConnectionState.Offline)
                }
            }
        } catch (e: Exception) {
            Result.success(CloudConnectionState.Offline)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tokenStorage.clearTokens(PROVIDER_ID)
            pendingVerifier = null
            pendingClientId = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
