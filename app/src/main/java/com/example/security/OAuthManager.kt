package com.example.security

import android.net.Uri
import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val tokenType: String,
    val scope: String?
)

/**
 * Standard PKCE and OAuth 2.0 helper for connecting Multi-Cloud providers
 * directly from the Android client to official cloud APIs without any third-party backend.
 */
class OAuthManager(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val REDIRECT_URI = "cloudgallery://oauth/callback"

        // Official OAuth Endpoints
        const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val GOOGLE_PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/photoslibrary.appendonly"

        const val ONEDRIVE_AUTH_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        const val ONEDRIVE_TOKEN_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        const val ONEDRIVE_SCOPE = "Files.ReadWrite offline_access User.Read"

        const val DROPBOX_AUTH_ENDPOINT = "https://www.dropbox.com/oauth2/authorize"
        const val DROPBOX_TOKEN_ENDPOINT = "https://api.dropboxapi.com/oauth2/token"
        const val DROPBOX_SCOPE = "files.metadata.read files.content.read files.content.write account_info.read"
    }

    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun buildAuthorizationUrl(
        providerId: String,
        clientId: String,
        codeChallenge: String,
        state: String
    ): String {
        val (authEndpoint, scope) = when (providerId) {
            "google_photos" -> Pair(GOOGLE_AUTH_ENDPOINT, GOOGLE_PHOTOS_SCOPE)
            "onedrive" -> Pair(ONEDRIVE_AUTH_ENDPOINT, ONEDRIVE_SCOPE)
            "dropbox" -> Pair(DROPBOX_AUTH_ENDPOINT, DROPBOX_SCOPE)
            else -> throw IllegalArgumentException("Unsupported OAuth provider: $providerId")
        }

        val uriBuilder = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)

        if (scope.isNotEmpty()) {
            uriBuilder.appendQueryParameter("scope", scope)
        }

        if (providerId == "google_photos") {
            uriBuilder.appendQueryParameter("access_type", "offline")
            uriBuilder.appendQueryParameter("prompt", "consent")
        } else if (providerId == "dropbox") {
            uriBuilder.appendQueryParameter("token_access_type", "offline")
        }

        return uriBuilder.build().toString()
    }

    suspend fun exchangeCodeForToken(
        providerId: String,
        clientId: String,
        clientSecret: String? = null,
        code: String,
        codeVerifier: String
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val tokenEndpoint = when (providerId) {
                "google_photos" -> GOOGLE_TOKEN_ENDPOINT
                "onedrive" -> ONEDRIVE_TOKEN_ENDPOINT
                "dropbox" -> DROPBOX_TOKEN_ENDPOINT
                else -> return@withContext Result.failure(IllegalArgumentException("Unsupported provider: $providerId"))
            }

            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)

            if (!clientSecret.isNullOrEmpty()) {
                formBuilder.add("client_secret", clientSecret)
            }

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBuilder.build())
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Token exchange failed (${response.code}): ${parseErrorMessage(body)}")
                    )
                }

                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                val expiresIn = json.optLong("expires_in", 3600L)
                val tokenType = json.optString("token_type", "Bearer")
                val scope = json.optString("scope")

                if (accessToken.isEmpty()) {
                    return@withContext Result.failure(Exception("Response did not contain an access_token"))
                }

                Result.success(
                    OAuthTokenResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                        tokenType = tokenType,
                        scope = scope.takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(
        providerId: String,
        clientId: String,
        refreshToken: String,
        clientSecret: String? = null
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val tokenEndpoint = when (providerId) {
                "google_photos" -> GOOGLE_TOKEN_ENDPOINT
                "onedrive" -> ONEDRIVE_TOKEN_ENDPOINT
                "dropbox" -> DROPBOX_TOKEN_ENDPOINT
                else -> return@withContext Result.failure(IllegalArgumentException("Unsupported provider: $providerId"))
            }

            val formBuilder = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)

            if (!clientSecret.isNullOrEmpty()) {
                formBuilder.add("client_secret", clientSecret)
            }

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBuilder.build())
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Token refresh failed (${response.code}): ${parseErrorMessage(body)}")
                    )
                }

                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() } ?: refreshToken
                val expiresIn = json.optLong("expires_in", 3600L)
                val tokenType = json.optString("token_type", "Bearer")
                val scope = json.optString("scope")

                Result.success(
                    OAuthTokenResponse(
                        accessToken = accessToken,
                        refreshToken = newRefreshToken,
                        expiresInSeconds = expiresIn,
                        tokenType = tokenType,
                        scope = scope.takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            json.optString("error_description")
                .ifEmpty { json.optString("error") }
                .ifEmpty { json.optString("message") }
                .ifEmpty { "Unknown OAuth error" }
        } catch (_: Exception) {
            jsonBody.take(120)
        }
    }
}
