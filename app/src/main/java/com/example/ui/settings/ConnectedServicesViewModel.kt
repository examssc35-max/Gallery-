package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudStorageQuota
import com.example.domain.model.multicloud.ProviderConnectionInfo
import com.example.domain.repository.MultiCloudRepository
import com.example.domain.repository.R2Repository
import com.example.security.OAuthManager
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
    val message: String? = null,
    val errorMessage: String? = null
)

class ConnectedServicesViewModel(
    private val multiCloudRepository: MultiCloudRepository,
    private val r2Repository: R2Repository,
    private val preferencesManager: PreferencesManager,
    private val oAuthManager: OAuthManager = OAuthManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedServicesUiState())
    val uiState: StateFlow<ConnectedServicesUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
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
            val result = multiCloudRepository.disconnectProvider(info.providerId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    message = "${info.displayName} disconnected.",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to disconnect: ${result.exceptionOrNull()?.message}",
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
            val params = mapOf(
                "accountId" to accountId.trim(),
                "accessKeyId" to accessKeyId.trim(),
                "secretAccessKey" to secretAccessKey.trim(),
                "bucketName" to bucketName.trim(),
                "endpoint" to endpoint.trim()
            )
            val result = multiCloudRepository.connectProvider("r2", params)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedProviderForConfig = null,
                    message = "Connected to Cloudflare R2 successfully!",
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

    fun connectOAuthProvider(
        providerId: String,
        accessToken: String,
        refreshToken: String? = null,
        clientId: String? = null,
        email: String? = null,
        name: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val params = mutableMapOf(
                "accessToken" to accessToken.trim()
            )
            if (!refreshToken.isNullOrBlank()) params["refreshToken"] = refreshToken.trim()
            if (!clientId.isNullOrBlank()) params["clientId"] = clientId.trim()
            if (!email.isNullOrBlank()) params["email"] = email.trim()
            if (!name.isNullOrBlank()) params["name"] = name.trim()

            val result = multiCloudRepository.connectProvider(providerId, params)
            if (result.isSuccess) {
                val providerName = multiCloudRepository.getProvider(providerId)?.displayName ?: providerId
                _uiState.value = _uiState.value.copy(
                    selectedProviderForConfig = null,
                    message = "Connected to $providerName successfully!",
                    isLoading = false
                )
                loadProviders()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed",
                    isLoading = false
                )
            }
        }
    }

    fun exchangeOAuthCode(
        providerId: String,
        code: String,
        clientId: String,
        codeVerifier: String,
        clientSecret: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val tokenRes = oAuthManager.exchangeCodeForToken(
                providerId = providerId,
                clientId = clientId.trim(),
                clientSecret = clientSecret?.trim(),
                code = code.trim(),
                codeVerifier = codeVerifier
            )

            if (tokenRes.isSuccess) {
                val resp = tokenRes.getOrThrow()
                connectOAuthProvider(
                    providerId = providerId,
                    accessToken = resp.accessToken,
                    refreshToken = resp.refreshToken,
                    clientId = clientId.trim()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = tokenRes.exceptionOrNull()?.message ?: "Token exchange failed",
                    isLoading = false
                )
            }
        }
    }

    fun buildAuthUrl(
        providerId: String,
        clientId: String,
        codeChallenge: String
    ): String {
        return oAuthManager.buildAuthorizationUrl(
            providerId = providerId,
            clientId = clientId.trim(),
            codeChallenge = codeChallenge,
            state = "state_$providerId"
        )
    }

    fun generateCodeVerifier(): String = oAuthManager.generateCodeVerifier()
    fun generateCodeChallenge(verifier: String): String = oAuthManager.generateCodeChallenge(verifier)

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }
}
