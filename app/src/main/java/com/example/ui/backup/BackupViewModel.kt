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
    val stats: BackupStats = BackupStats(0, 0, 0, 0, 0L),
    val records: List<BackupRecordEntity> = emptyList(),
    val isRunningBackup: Boolean = false,
    val currentBackupProgress: String? = null,
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
    }

    private fun observeData() {
        viewModelScope.launch {
            backupRepository.backupSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            r2Repository.credentialsFlow.collect { creds ->
                _uiState.value = _uiState.value.copy(credentials = creds)
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
            val updated = _uiState.value.settings.copy(isAutoBackupEnabled = enabled)
            backupRepository.updateBackupSettings(updated)
        }
    }

    fun toggleBackupVideos(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(backupVideos = enabled)
            backupRepository.updateBackupSettings(updated)
        }
    }

    fun toggleBackupPhotos(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(backupPhotos = enabled)
            backupRepository.updateBackupSettings(updated)
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
            _uiState.value = _uiState.value.copy(
                isRunningBackup = true,
                currentBackupProgress = "Starting backup..."
            )

            val uploadedCount = backupRepository.runBackupPass { current, total, item ->
                _uiState.value = _uiState.value.copy(
                    currentBackupProgress = "Backing up $current of $total: ${item.name}"
                )
            }

            _uiState.value = _uiState.value.copy(
                isRunningBackup = false,
                currentBackupProgress = null,
                statusMessage = if (uploadedCount > 0) "Backup complete: $uploadedCount items uploaded" else "Everything is up to date!"
            )
            refreshStats()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningBackup = true,
                currentBackupProgress = "Retrying failed uploads..."
            )
            backupRepository.retryFailedUploads()
            _uiState.value = _uiState.value.copy(
                isRunningBackup = false,
                currentBackupProgress = null,
                statusMessage = "Retried failed uploads"
            )
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
