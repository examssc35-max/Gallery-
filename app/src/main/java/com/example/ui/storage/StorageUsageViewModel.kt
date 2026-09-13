package com.example.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.StorageUsage
import com.example.domain.repository.R2Repository
import com.example.domain.usecase.GetStorageUsageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StorageUsageUiState {
    data object Loading : StorageUsageUiState()

    data class NotConnected(
        val message: String = "Cloud storage isn't connected."
    ) : StorageUsageUiState()

    data class Success(
        val usage: StorageUsage,
        val isRefreshing: Boolean = false,
        val progressText: String? = null
    ) : StorageUsageUiState()

    data class Error(
        val message: String,
        val cachedUsage: StorageUsage? = null
    ) : StorageUsageUiState()
}

class StorageUsageViewModel(
    private val getStorageUsageUseCase: GetStorageUsageUseCase,
    private val r2Repository: R2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StorageUsageUiState>(StorageUsageUiState.Loading)
    val uiState: StateFlow<StorageUsageUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var isConnected: Boolean = false
    private var bucketName: String = ""

    init {
        observeCredentialsAndCache()
    }

    private fun observeCredentialsAndCache() {
        // 1. Observe credentials
        viewModelScope.launch {
            r2Repository.credentialsFlow.collect { creds ->
                isConnected = creds.isVerified && creds.secretAccessKey.isNotBlank() && creds.bucketName.isNotBlank()
                bucketName = creds.bucketName

                if (!isConnected) {
                    _uiState.value = StorageUsageUiState.NotConnected("Cloud storage isn't connected.")
                } else {
                    // Connected: check if we have a current state or need to load cache
                    val current = _uiState.value
                    if (current is StorageUsageUiState.NotConnected || current is StorageUsageUiState.Loading) {
                        val cached = getStorageUsageUseCase.getCachedStorageUsage()
                        if (cached != null) {
                            _uiState.value = StorageUsageUiState.Success(usage = cached)
                        } else {
                            // No cache yet: trigger initial scan
                            refresh(isInitial = true)
                        }
                    }
                }
            }
        }

        // 2. Observe cached storage usage for live updates (e.g. after upload, delete, auto-backup)
        viewModelScope.launch {
            getStorageUsageUseCase.cachedStorageUsageFlow.collect { cached ->
                if (!isConnected) return@collect
                if (cached != null) {
                    val current = _uiState.value
                    if (current is StorageUsageUiState.Success) {
                        _uiState.value = current.copy(usage = cached)
                    } else if (current is StorageUsageUiState.Loading || current is StorageUsageUiState.Error) {
                        _uiState.value = StorageUsageUiState.Success(usage = cached)
                    }
                }
            }
        }
    }

    fun refresh(isInitial: Boolean = false) {
        if (!isConnected) {
            _uiState.value = StorageUsageUiState.NotConnected("Cloud storage isn't connected.")
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val currentState = _uiState.value
            val existingUsage = when (currentState) {
                is StorageUsageUiState.Success -> currentState.usage
                is StorageUsageUiState.Error -> currentState.cachedUsage
                else -> null
            }

            if (existingUsage != null) {
                _uiState.value = StorageUsageUiState.Success(
                    usage = existingUsage,
                    isRefreshing = true,
                    progressText = "Connecting to R2..."
                )
            } else {
                _uiState.value = StorageUsageUiState.Loading
            }

            val result = withContext(Dispatchers.IO) {
                getStorageUsageUseCase.calculateStorageUsage(
                    onProgress = { scannedCount, scannedBytes ->
                        val current = _uiState.value
                        if (current is StorageUsageUiState.Success) {
                            _uiState.value = current.copy(
                                progressText = "Scanning objects... ($scannedCount found)"
                            )
                        }
                    }
                )
            }

            if (result.isSuccess) {
                val newUsage = result.getOrThrow()
                _uiState.value = StorageUsageUiState.Success(
                    usage = newUsage,
                    isRefreshing = false,
                    progressText = null
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Couldn't calculate storage usage."
                _uiState.value = StorageUsageUiState.Error(
                    message = errorMsg,
                    cachedUsage = existingUsage
                )
            }
        }
    }
}
