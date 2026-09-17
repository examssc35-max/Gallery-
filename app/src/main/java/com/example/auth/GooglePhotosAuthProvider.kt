package com.example.auth

import android.net.Uri
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.security.OAuthManager
import com.example.security.SecureCloudTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GooglePhotosAuthProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val PROVIDER_ID = "google_photos"
        const val DISPLAY_NAME = "Google Photos"
        const val REDIRECT_URI = "cloudgallery://oauth/google-photos"
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
    }

    private var pendingVerifier: String? = null
    private var pendingClientId: String? = null

    fun buildAuthorizationUrl(customClientId: String?): String {
        val clientId = customClientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("A valid Google Cloud OAuth Client ID is required to authorize with Google.")
        val verifier = oAuthManager.generateCodeVerifier()
        val challenge = oAuthManager.generateCodeChallenge(verifier)
        pendingVerifier = verifier
        pendingClientId = clientId

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("include_granted_scopes", "true")
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
                ?: tokenStorage.getClientId(PROVIDER_ID)
                ?: return@withContext Result.failure(IllegalStateException("No Google OAuth Client ID found for authorization."))
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
                    return@withContext Result.failure(Exception("Google authorization failed (${response.code}): $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                // Fetch user profile info
                var accountName = "Google Account"
                var accountEmail: String? = null
                try {
                    val userReq = Request.Builder()
                        .url("https://www.googleapis.com/oauth2/v3/userinfo")
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    httpClient.newCall(userReq).execute().use { userResp ->
                        if (userResp.isSuccessful) {
                            val userJson = JSONObject(userResp.body?.string().orEmpty())
                            accountName = userJson.optString("name", "Google Account")
                            accountEmail = userJson.optString("email").takeIf { it.isNotEmpty() }
                        }
                    }
                } catch (_: Exception) {}

                // Verify access to Photos Library API
                val verifyReq = Request.Builder()
                    .url("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=1")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                httpClient.newCall(verifyReq).execute().use { verifyResp ->
                    if (!verifyResp.isSuccessful && verifyResp.code != 404) {
                        val verifyErr = verifyResp.body?.string().orEmpty()
                        return@withContext Result.failure(Exception("Google Photos API verification failed (${verifyResp.code}): $verifyErr"))
                    }
                }

                // Save securely in Keystore-backed storage
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
                        serviceInfo = "Google Photos Library API"
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
                .url("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=1")
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) {
                    Result.success(
                        CloudConnectionState.Connected(
                            accountName = tokens.accountName ?: "Google Account",
                            accountEmail = tokens.accountEmail,
                            serviceInfo = "Google Photos Library API"
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
