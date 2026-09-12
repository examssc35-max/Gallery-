package com.example.ui.cloud

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.R2Credentials
import com.example.domain.model.R2Item
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CloudBrowserUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val currentPrefix: String = "",
    val items: List<R2Item> = emptyList(),
    val error: String? = null,
    val credentials: R2Credentials = R2Credentials(),
    val activeTransferMessage: String? = null,
    val activeTransferProgress: Float? = null // 0.0 to 1.0
)

class CloudBrowserViewModel(
    private val r2Repository: R2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudBrowserUiState())
    val uiState: StateFlow<CloudBrowserUiState> = _uiState.asStateFlow()

    init {
        observeCredentials()
    }

    private fun observeCredentials() {
        viewModelScope.launch {
            r2Repository.credentialsFlow.collect { creds ->
                _uiState.value = _uiState.value.copy(
                    credentials = creds,
                    isConnected = creds.isVerified && creds.secretAccessKey.isNotEmpty()
                )
                if (creds.isVerified && creds.secretAccessKey.isNotEmpty()) {
                    loadObjects(_uiState.value.currentPrefix)
                }
            }
        }
    }

    fun loadObjects(prefix: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                currentPrefix = prefix,
                error = null
            )
            val result = r2Repository.listObjects(prefix)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = result.getOrDefault(emptyList())
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to list objects"
                )
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPrefix
        if (current.isEmpty()) return
        val stripped = current.removeSuffix("/")
        val lastSlash = stripped.lastIndexOf('/')
        val parent = if (lastSlash == -1) "" else stripped.substring(0, lastSlash + 1)
        loadObjects(parent)
    }

    fun downloadItem(context: Context, item: R2Item, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                activeTransferMessage = "Downloading ${item.name}...",
                activeTransferProgress = 0f
            )

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, item.name)

            val result = r2Repository.downloadObject(item, targetFile) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0.5f
                _uiState.value = _uiState.value.copy(activeTransferProgress = progress)
            }

            _uiState.value = _uiState.value.copy(
                activeTransferMessage = null,
                activeTransferProgress = null
            )

            if (result.isSuccess) {
                onComplete(true, "Saved to Downloads: ${targetFile.name}")
            } else {
                onComplete(false, result.exceptionOrNull()?.message ?: "Download failed")
            }
        }
    }

    fun deleteItem(item: R2Item, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = r2Repository.deleteObject(item.key)
            _uiState.value = _uiState.value.copy(isLoading = false)

            if (result.isSuccess) {
                loadObjects(_uiState.value.currentPrefix)
                onComplete(true, "Deleted from R2: ${item.name}")
            } else {
                onComplete(false, result.exceptionOrNull()?.message ?: "Delete failed")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
