package com.example.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.multiCloudDataStore by preferencesDataStore(name = "multi_cloud_storage_prefs")

data class OAuthTokenData(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val accountName: String?,
    val accountEmail: String?,
    val clientId: String?,
    val isConnected: Boolean
) {
    val isExpired: Boolean
        get() = expiresAt > 0L && System.currentTimeMillis() > expiresAt
}

/**
 * Keystore-backed secure token storage for Multi-Cloud providers.
 * All sensitive OAuth tokens (access tokens, refresh tokens) are encrypted
 * via AndroidKeyStore AES-256-GCM before writing to storage.
 */
class SecureCloudTokenStorage(
    private val context: Context,
    private val keystoreManager: KeystoreManager = KeystoreManager()
) {

    private fun keyEncryptedAccess(providerId: String) =
        stringPreferencesKey("${providerId}_enc_access_token")

    private fun keyEncryptedRefresh(providerId: String) =
        stringPreferencesKey("${providerId}_enc_refresh_token")

    private fun keyExpiresAt(providerId: String) =
        longPreferencesKey("${providerId}_expires_at")

    private fun keyAccountName(providerId: String) =
        stringPreferencesKey("${providerId}_account_name")

    private fun keyAccountEmail(providerId: String) =
        stringPreferencesKey("${providerId}_account_email")

    private fun keyClientId(providerId: String) =
        stringPreferencesKey("${providerId}_client_id")

    private fun keyIsConnected(providerId: String) =
        booleanPreferencesKey("${providerId}_is_connected")

    suspend fun saveTokens(
        providerId: String,
        accessToken: String,
        refreshToken: String? = null,
        expiresAt: Long = 0L,
        accountName: String? = null,
        accountEmail: String? = null,
        clientId: String? = null
    ) {
        val encAccess = if (accessToken.isNotEmpty()) keystoreManager.encrypt(accessToken) else ""
        val encRefresh = if (!refreshToken.isNullOrEmpty()) keystoreManager.encrypt(refreshToken) else ""

        context.multiCloudDataStore.edit { prefs ->
            prefs[keyEncryptedAccess(providerId)] = encAccess
            if (encRefresh.isNotEmpty()) {
                prefs[keyEncryptedRefresh(providerId)] = encRefresh
            }
            prefs[keyExpiresAt(providerId)] = expiresAt
            if (accountName != null) prefs[keyAccountName(providerId)] = accountName
            if (accountEmail != null) prefs[keyAccountEmail(providerId)] = accountEmail
            if (clientId != null) prefs[keyClientId(providerId)] = clientId
            prefs[keyIsConnected(providerId)] = accessToken.isNotEmpty()
        }
    }

    suspend fun getTokens(providerId: String): OAuthTokenData? {
        val prefs = context.multiCloudDataStore.data.first()
        val isConnected = prefs[keyIsConnected(providerId)] ?: false
        if (!isConnected) return null

        val encAccess = prefs[keyEncryptedAccess(providerId)] ?: ""
        val encRefresh = prefs[keyEncryptedRefresh(providerId)] ?: ""
        val accessToken = keystoreManager.decrypt(encAccess)
        val refreshToken = if (encRefresh.isNotEmpty()) keystoreManager.decrypt(encRefresh) else null

        if (accessToken.isEmpty()) return null

        return OAuthTokenData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = prefs[keyExpiresAt(providerId)] ?: 0L,
            accountName = prefs[keyAccountName(providerId)],
            accountEmail = prefs[keyAccountEmail(providerId)],
            clientId = prefs[keyClientId(providerId)],
            isConnected = true
        )
    }

    fun getTokensFlow(providerId: String): Flow<OAuthTokenData?> {
        return context.multiCloudDataStore.data.map { prefs ->
            val isConnected = prefs[keyIsConnected(providerId)] ?: false
            if (!isConnected) return@map null

            val encAccess = prefs[keyEncryptedAccess(providerId)] ?: ""
            val encRefresh = prefs[keyEncryptedRefresh(providerId)] ?: ""
            val accessToken = keystoreManager.decrypt(encAccess)
            val refreshToken = if (encRefresh.isNotEmpty()) keystoreManager.decrypt(encRefresh) else null

            if (accessToken.isEmpty()) null
            else OAuthTokenData(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = prefs[keyExpiresAt(providerId)] ?: 0L,
                accountName = prefs[keyAccountName(providerId)],
                accountEmail = prefs[keyAccountEmail(providerId)],
                clientId = prefs[keyClientId(providerId)],
                isConnected = true
            )
        }
    }

    suspend fun getClientId(providerId: String): String? {
        val prefs = context.multiCloudDataStore.data.first()
        return prefs[keyClientId(providerId)]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveClientId(providerId: String, clientId: String) {
        context.multiCloudDataStore.edit { prefs ->
            prefs[keyClientId(providerId)] = clientId.trim()
        }
    }

    suspend fun clearTokens(providerId: String) {
        context.multiCloudDataStore.edit { prefs ->
            prefs.remove(keyEncryptedAccess(providerId))
            prefs.remove(keyEncryptedRefresh(providerId))
            prefs.remove(keyExpiresAt(providerId))
            prefs.remove(keyAccountName(providerId))
            prefs.remove(keyAccountEmail(providerId))
            prefs.remove(keyClientId(providerId))
            prefs[keyIsConnected(providerId)] = false
        }
    }

    suspend fun isProviderConnected(providerId: String): Boolean {
        val prefs = context.multiCloudDataStore.data.first()
        return prefs[keyIsConnected(providerId)] == true
    }
}
