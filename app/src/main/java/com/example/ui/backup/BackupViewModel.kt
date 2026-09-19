package com.example.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BackupSettings
import com.example.data.local.R2Credentials
import com.example.data.local.entity.BackupRecordEntity
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.BackupStats
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val settings: BackupSettings = BackupSettings(),
    val credentials: R2Credentials = R2Credentials(),
    val isR2Connected: Boolean = false,
    val stats: BackupStats = BackupStats(0, 0, 0, 0, 0L),
    val records: List<BackupRecordEntity> = emptyList(),
    val isRunningBackup: Boolean = false,
    val currentBackupProgress: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null
)

class BackupViewModel(
    private val backupRepository: BackupRepository,
    private val r2Repository: R2Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        observeData()
        refreshStats()
    }

    private fun observeData() {
        viewModelScope.launch {
            backupRepository.backupSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            r2Repository.credentialsFlow.collect { creds ->
                val connected = creds.isVerified || (creds.secretAccessKey.isNotBlank() && creds.bucketName.isNotBlank() && creds.accountId.isNotBlank())
                _uiState.value = _uiState.value.copy(
                    credentials = creds,
                    isR2Connected = connected
                )
            }
        }
        viewModelScope.launch {
            backupRepository.backupRecordsFlow.collect { list ->
                _uiState.value = _uiState.value.copy(records = list)
                refreshStats()
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = backupRepository.getBackupStats()
            _uiState.value = _uiState.value.copy(stats = stats)
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_uiState.value.isR2Connected) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Connect Cloudflare R2 to enable automatic backup"
                )
                return@launch
            }
            val updated = _uiState.value.settings.copy(isAutoBackupEnabled = enabled)
            backupRepository.updateBackupSettings(updated)
            refreshStats()
        }
    }

    fun toggleBackupVideos(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(backupVideos = enabled)
            backupRepository.updateBackupSettings(updated)
            refreshStats()
        }
    }

    fun toggleBackupPhotos(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(backupPhotos = enabled)
            backupRepository.updateBackupSettings(updated)
            refreshStats()
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(wifiOnly = enabled)
            backupRepository.updateBackupSettings(updated)
        }
    }

    fun toggleRequireCharging(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(requireCharging = enabled)
            backupRepository.updateBackupSettings(updated)
        }
    }

    fun triggerBackupNow() {
        viewModelScope.launch {
            if (!_uiState.value.isR2Connected) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Connect Cloudflare R2 to enable automatic backup"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRunningBackup = true,
                currentBackupProgress = "Scanning media...",
                progressCurrent = 0,
                progressTotal = 0
            )

            try {
                val uploadedCount = backupRepository.runBackupPass(force = true) { current, total, item ->
                    _uiState.value = _uiState.value.copy(
                        currentBackupProgress = "Backing up $current of $total: ${item.name}",
                        progressCurrent = current,
                        progressTotal = total
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isRunningBackup = false,
                    currentBackupProgress = null,
                    progressCurrent = 0,
                    progressTotal = 0,
                    statusMessage = if (uploadedCount > 0) "Backup complete: $uploadedCount items uploaded" else "Everything is up to date!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBackup = false,
                    currentBackupProgress = null,
                    progressCurrent = 0,
                    progressTotal = 0,
                    statusMessage = "Backup failed: ${e.localizedMessage ?: e.message ?: "Network error"}"
                )
            }
            refreshStats()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            if (!_uiState.value.isR2Connected) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Connect Cloudflare R2 to enable automatic backup"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRunningBackup = true,
                currentBackupProgress = "Retrying failed uploads..."
            )

            try {
                backupRepository.retryFailedUploads()
                _uiState.value = _uiState.value.copy(
                    isRunningBackup = false,
                    currentBackupProgress = null,
                    statusMessage = "Retried failed uploads"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBackup = false,
                    currentBackupProgress = null,
                    statusMessage = "Retry failed: ${e.localizedMessage ?: e.message}"
                )
            }
            refreshStats()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            backupRepository.clearHistory()
            refreshStats()
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}
