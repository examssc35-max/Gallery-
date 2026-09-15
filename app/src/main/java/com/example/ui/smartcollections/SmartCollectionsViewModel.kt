package com.example.ui.smartcollections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.domain.model.AnalysisRunState
import com.example.domain.model.AnalysisStatus
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartCollection
import com.example.domain.model.SmartCollectionSettings
import com.example.domain.repository.SmartCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartCollectionsUiState(
    val collections: List<SmartCollection> = emptyList(),
    val analysisStatus: AnalysisStatus = AnalysisStatus(),
    val isModelInstalled: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val modelDownloadProgress: Float = 0f,
    val settings: SmartCollectionSettings = SmartCollectionSettings(),
    val errorMessage: String? = null
) {
    val isAnalyzing: Boolean
        get() = analysisStatus.state == AnalysisRunState.ANALYZING
}

class SmartCollectionsViewModel(
    private val smartCollectionRepository: SmartCollectionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isDownloadingModel = MutableStateFlow(false)
    private val _modelDownloadProgress = MutableStateFlow(0f)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val settingsFlow: StateFlow<SmartCollectionSettings> = combine(
        preferencesManager.smartCollectionsEnabledFlow,
        preferencesManager.smartCollectionsAutoAnalyzeFlow,
        preferencesManager.smartCollectionsAnalyzeVideosFlow,
        preferencesManager.smartCollectionsWifiOnlyFlow,
        preferencesManager.smartCollectionsRequireChargingFlow
    ) { enabled, autoAnalyze, analyzeVideos, wifiOnly, charging ->
        SmartCollectionSettings(
            isEnabled = enabled,
            autoAnalyzeNewMedia = autoAnalyze,
            analyzeVideos = analyzeVideos,
            wifiOnly = wifiOnly,
            requireCharging = charging,
            isModelInstalled = false
        )
    }.combine(preferencesManager.smartCollectionsModelInstalledFlow) { settings, installed ->
        settings.copy(isModelInstalled = installed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartCollectionSettings()
    )

    val uiState: StateFlow<SmartCollectionsUiState> = combine(
        smartCollectionRepository.getSmartCollectionsFlow(),
        smartCollectionRepository.getAnalysisStatusFlow(),
        _isDownloadingModel,
        _modelDownloadProgress,
        settingsFlow
    ) { collections, status, downloading, dlProgress, settings ->
        SmartCollectionsUiState(
            collections = collections,
            analysisStatus = status,
            isModelInstalled = settings.isModelInstalled,
            isDownloadingModel = downloading,
            modelDownloadProgress = dlProgress,
            settings = settings,
            errorMessage = _errorMessage.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartCollectionsUiState()
    )

    init {
        checkAndTriggerInitialAnalysis()
    }

    private fun checkAndTriggerInitialAnalysis() {
        viewModelScope.launch {
            val isInstalled = smartCollectionRepository.isModelInstalled()
            if (isInstalled && settingsFlow.value.isEnabled && settingsFlow.value.autoAnalyzeNewMedia) {
                smartCollectionRepository.analyzeUnprocessedMedia(forceAll = false)
            }
        }
    }

    fun downloadAndInstallModel() {
        if (_isDownloadingModel.value) return
        _isDownloadingModel.value = true
        _modelDownloadProgress.value = 0f
        _errorMessage.value = null

        viewModelScope.launch {
            val result = smartCollectionRepository.installModel { progress ->
                _modelDownloadProgress.value = progress
            }
            _isDownloadingModel.value = false
            if (result.isSuccess) {
                // Kick off analysis automatically once model is ready
                smartCollectionRepository.analyzeUnprocessedMedia(forceAll = false)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to install model"
            }
        }
    }

    fun startAnalysis(forceAll: Boolean = false) {
        viewModelScope.launch {
            val result = smartCollectionRepository.analyzeUnprocessedMedia(forceAll)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun pauseAnalysis() {
        smartCollectionRepository.pauseAnalysis()
    }

    fun clearClassificationData() {
        viewModelScope.launch {
            smartCollectionRepository.clearClassificationData()
        }
    }

    fun reanalyzeAll() {
        viewModelScope.launch {
            smartCollectionRepository.reanalyzeAllMedia()
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartCollectionsEnabled(enabled)
            smartCollectionRepository.scheduleWorker()
        }
    }

    fun setAutoAnalyze(auto: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartCollectionsAutoAnalyze(auto)
        }
    }

    fun setAnalyzeVideos(videos: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartCollectionsAnalyzeVideos(videos)
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartCollectionsWifiOnly(wifiOnly)
            smartCollectionRepository.scheduleWorker()
        }
    }

    fun setRequireCharging(charging: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSmartCollectionsRequireCharging(charging)
            smartCollectionRepository.scheduleWorker()
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getCollectionMedia(categoryId: String) = smartCollectionRepository.getCollectionMediaFlow(categoryId)
}
