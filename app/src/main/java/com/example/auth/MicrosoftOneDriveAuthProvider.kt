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

class MicrosoftOneDriveAuthProvider(
    private val tokenStorage: SecureCloudTokenStorage,
    private val oAuthManager: OAuthManager = OAuthManager(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val PROVIDER_ID = "onedrive"
        const val DISPLAY_NAME = "Microsoft OneDrive"
        const val REDIRECT_URI = "cloudgallery://oauth/onedrive"
        const val AUTH_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        const val SCOPE = "Files.Read Files.Read.All User.Read offline_access"

        // Public fallback client ID for mobile PKCE if none is provided
        const val DEFAULT_CLIENT_ID = "d3590ed6-52b3-4102-aeff-aad2292ab01c"
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
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPE)
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
                    return@withContext Result.failure(Exception("Microsoft authorization failed (${response.code}): $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                // Verify access to Microsoft Graph
                var accountName = "Microsoft User"
                var accountEmail: String? = null

                val driveReq = Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive")
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                httpClient.newCall(driveReq).execute().use { driveResp ->
                    if (driveResp.isSuccessful) {
                        val driveJson = JSONObject(driveResp.body?.string().orEmpty())
                        val ownerObj = driveJson.optJSONObject("owner")?.optJSONObject("user")
                        if (ownerObj != null) {
                            accountName = ownerObj.optString("displayName", "Microsoft User")
                            accountEmail = ownerObj.optString("email").takeIf { it.isNotEmpty() }
                                ?: ownerObj.optString("userPrincipalName")
                        }
                    } else {
                        val err = driveResp.body?.string().orEmpty()
                        return@withContext Result.failure(Exception("Microsoft Graph verification failed (${driveResp.code}): $err"))
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
                        serviceInfo = "Microsoft Graph Storage"
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
                .url("https://graph.microsoft.com/v1.0/me/drive")
                .header("Authorization", "Bearer ${tokens.accessToken}")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(
                        CloudConnectionState.Connected(
                            accountName = tokens.accountName ?: "Microsoft User",
                            accountEmail = tokens.accountEmail,
                            serviceInfo = "Microsoft Graph Storage"
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
