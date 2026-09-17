package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.CloudflareR2CredentialProvider
import com.example.auth.DropboxAuthProvider
import com.example.auth.GoogleDriveAuthProvider
import com.example.auth.GooglePhotosAuthProvider
import com.example.auth.MicrosoftOneDriveAuthProvider
import com.example.data.local.PreferencesManager
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.ProviderConnectionInfo
import com.example.domain.repository.MultiCloudRepository
import com.example.domain.repository.R2Repository
import com.example.security.SecureCloudTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectedServicesUiState(
    val providers: List<ProviderConnectionInfo> = emptyList(),
    val isLoading: Boolean = false,
    val selectedProviderForConfig: String? = null,
    val selectedProviderForCapabilities: ProviderConnectionInfo? = null,
    val providerToDisconnect: ProviderConnectionInfo? = null,
    val isAuthenticating: Boolean = false,
    val authenticatingProviderName: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class ConnectedServicesViewModel(
    private val multiCloudRepository: MultiCloudRepository,
    private val r2Repository: R2Repository,
    private val preferencesManager: PreferencesManager,
    private val tokenStorage: SecureCloudTokenStorage,
    val googlePhotosAuthProvider: GooglePhotosAuthProvider = GooglePhotosAuthProvider(tokenStorage),
    val googleDriveAuthProvider: GoogleDriveAuthProvider = GoogleDriveAuthProvider(tokenStorage),
    val microsoftOneDriveAuthProvider: MicrosoftOneDriveAuthProvider = MicrosoftOneDriveAuthProvider(tokenStorage),
    val dropboxAuthProvider: DropboxAuthProvider = DropboxAuthProvider(tokenStorage),
    val cloudflareR2CredentialProvider: CloudflareR2CredentialProvider = CloudflareR2CredentialProvider(r2Repository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedServicesUiState())
    val uiState: StateFlow<ConnectedServicesUiState> = _uiState.asStateFlow()

    private val _savedClientIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val savedClientIds: StateFlow<Map<String, String>> = _savedClientIds.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val clientIds = mutableMapOf<String, String>()
            listOf("google_photos", "google_drive", "onedrive", "dropbox").forEach { pid ->
                tokenStorage.getClientId(pid)?.let { clientIds[pid] = it }
            }
            _savedClientIds.value = clientIds

            val all = multiCloudRepository.getAllProviders()
            val list = all.map { provider ->
                val state = provider.getConnectionState()
                val quota = if (state.isConnected) provider.getStorageUsage().getOrNull() else null
                ProviderConnectionInfo(
                    providerId = provider.providerId,
                    displayName = provider.displayName,
                    connectionState = state,
                    capabilities = provider.capabilities,
                    storageQuota = quota
                )
            }
            _uiState.value = _uiState.value.copy(
                providers = list,
                isLoading = false
            )
        }
    }

    fun openConfigure(providerId: String) {
        _uiState.value = _uiState.value.copy(selectedProviderForConfig = providerId)
    }

    fun closeConfigure() {
        _uiState.value = _uiState.value.copy(selectedProviderForConfig = null)
    }

    fun openCapabilities(info: ProviderConnectionInfo) {
        _uiState.value = _uiState.value.copy(selectedProviderForCapabilities = info)
    }

    fun closeCapabilities() {
        _uiState.value = _uiState.value.copy(selectedProviderForCapabilities = null)
    }

    fun requestDisconnect(info: ProviderConnectionInfo) {
        _uiState.value = _uiState.value.copy(providerToDisconnect = info)
    }

    fun dismissDisconnect() {
        _uiState.value = _uiState.value.copy(providerToDisconnect = null)
    }

    fun confirmDisconnect(info: ProviderConnectionInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(providerToDisconnect = null, isLoading = true)
            val disconnectResult = when (info.providerId) {
                "r2" -> cloudflareR2CredentialProvider.disconnect()
                "google_photos" -> googlePhotosAuthProvider.disconnect()
                "google_drive" -> googleDriveAuthProvider.disconnect()
                "onedrive" -> microsoftOneDriveAuthProvider.disconnect()
                "dropbox" -> dropboxAuthProvider.disconnect()
                else -> multiCloudRepository.disconnectProvider(info.providerId)
            }

            if (disconnectResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    message = "${info.displayName} disconnected. Cloud files were not deleted.",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to disconnect: ${disconnectResult.exceptionOrNull()?.message}",
                    isLoading = false
                )
            }
        }
    }

    fun getSavedClientId(providerId: String): String? {
        return _savedClientIds.value[providerId]
    }

    fun saveClientId(providerId: String, clientId: String) {
        val trimmed = clientId.trim()
        _savedClientIds.value = _savedClientIds.value + (providerId to trimmed)
        viewModelScope.launch {
            tokenStorage.saveClientId(providerId, trimmed)
        }
    }

    /**
     * Launches the official provider sign-in flow via the device browser.
     */
    fun startOfficialAuth(context: Context, providerId: String, customClientId: String? = null) {
        val cid = customClientId?.takeIf { it.isNotBlank() } ?: _savedClientIds.value[providerId]
        if (customClientId != null && customClientId.isNotBlank()) {
            saveClientId(providerId, customClientId)
        }

        val authUrl = try {
            when (providerId) {
                "google_photos" -> googlePhotosAuthProvider.buildAuthorizationUrl(cid)
                "google_drive" -> googleDriveAuthProvider.buildAuthorizationUrl(cid)
                "onedrive" -> microsoftOneDriveAuthProvider.buildAuthorizationUrl(cid)
                "dropbox" -> dropboxAuthProvider.buildAuthorizationUrl(cid)
                else -> null
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = e.message ?: "Authentication setup failed for $providerId"
            )
            openConfigure(providerId)
            return
        }

        if (authUrl == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Unknown provider: $providerId")
            return
        }

        val providerName = multiCloudRepository.getProvider(providerId)?.displayName ?: providerId
        _uiState.value = _uiState.value.copy(
            isAuthenticating = true,
            authenticatingProviderName = providerName,
            selectedProviderForConfig = null
        )

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isAuthenticating = false,
                authenticatingProviderName = null,
                errorMessage = "Failed to launch browser: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Handles the OAuth callback redirect (cloudgallery://oauth/<provider>?code=...).
     */
    fun handleAuthCallback(uri: Uri) {
        viewModelScope.launch {
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            if (error != null) {
                val msg = if (error == "access_denied") {
                    "Sign in was cancelled or denied."
                } else {
                    errorDescription ?: "Authentication failed: $error"
                }
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    authenticatingProviderName = null,
                    errorMessage = msg
                )
                return@launch
            }

            val code = uri.getQueryParameter("code")
            if (code.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    authenticatingProviderName = null,
                    errorMessage = "No authorization code received in callback."
                )
                return@launch
            }

            // Determine provider from host or path
            // e.g. cloudgallery://oauth/google-photos or cloudgallery://oauth/google-drive
            val path = uri.path?.trim('/') ?: ""
            val host = uri.host ?: ""
            val target = if (path.isNotEmpty()) path else host

            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = when {
                target.contains("google-photos", ignoreCase = true) -> {
                    googlePhotosAuthProvider.handleCallback(code)
                }
                target.contains("google-drive", ignoreCase = true) -> {
                    googleDriveAuthProvider.handleCallback(code)
                }
                target.contains("onedrive", ignoreCase = true) -> {
                    microsoftOneDriveAuthProvider.handleCallback(code)
                }
                target.contains("dropbox", ignoreCase = true) -> {
                    dropboxAuthProvider.handleCallback(code)
                }
                else -> {
                    // Try to deduce based on current authenticatingProviderName
                    when (_uiState.value.authenticatingProviderName) {
                        "Google Photos" -> googlePhotosAuthProvider.handleCallback(code)
                        "Google Drive" -> googleDriveAuthProvider.handleCallback(code)
                        "Microsoft OneDrive" -> microsoftOneDriveAuthProvider.handleCallback(code)
                        "Dropbox" -> dropboxAuthProvider.handleCallback(code)
                        else -> Result.failure(IllegalArgumentException("Unrecognized OAuth callback destination: $target"))
                    }
                }
            }

            if (result.isSuccess) {
                val conn = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    authenticatingProviderName = null,
                    selectedProviderForConfig = null,
                    message = "Connected to ${conn.serviceInfo ?: "Service"} as ${conn.accountName ?: "User"}!",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    authenticatingProviderName = null,
                    errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Fallback for manual authorization code exchange if user copies code from browser.
     */
    fun exchangeManualCode(providerId: String, code: String, customClientId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val cid = customClientId?.takeIf { it.isNotBlank() } ?: _savedClientIds.value[providerId]
            if (customClientId != null && customClientId.isNotBlank()) {
                saveClientId(providerId, customClientId)
            }
            val result = when (providerId) {
                "google_photos" -> googlePhotosAuthProvider.handleCallback(code, cid)
                "google_drive" -> googleDriveAuthProvider.handleCallback(code, cid)
                "onedrive" -> microsoftOneDriveAuthProvider.handleCallback(code, cid)
                "dropbox" -> dropboxAuthProvider.handleCallback(code, cid)
                else -> Result.failure(IllegalArgumentException("Unsupported OAuth provider: $providerId"))
            }

            if (result.isSuccess) {
                val conn = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    selectedProviderForConfig = null,
                    message = "Connected to ${conn.serviceInfo ?: providerId} successfully!",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Authorization failed",
                    isLoading = false
                )
            }
        }
    }

    fun connectR2(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String,
        endpoint: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = cloudflareR2CredentialProvider.saveAndVerify(
                accountId = accountId,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                bucketName = bucketName,
                endpoint = endpoint
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedProviderForConfig = null,
                    message = "Connected to Cloudflare R2 bucket '$bucketName'!",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to connect to Cloudflare R2",
                    isLoading = false
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }
}
