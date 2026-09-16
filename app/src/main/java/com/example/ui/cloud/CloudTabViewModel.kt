package com.example.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.SortOrder
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaClassifier
import com.example.domain.model.MediaItem
import com.example.domain.model.multicloud.CloudOperation
import com.example.domain.model.multicloud.CloudSearchRequest
import com.example.domain.model.multicloud.ProviderConnectionInfo
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.MultiCloudRepository
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloudTabUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val folders: List<String> = emptyList(),
    val currentPrefix: String = "",
    val searchQuery: String = "",
    val categoryFilter: MediaCategory? = null,
    val providerFilter: String = "all", // "all", "r2", "google_photos", "onedrive", "dropbox"
    val connectedProviders: List<ProviderConnectionInfo> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val errorMessage: String? = null,
    val nextContinuationToken: String? = null,
    val hasMore: Boolean = false,
    val isFlattenFolders: Boolean = false,
    val isOffline: Boolean = false
)

class CloudTabViewModel(
    private val r2Repository: R2Repository,
    private val backupRepository: BackupRepository,
    private val preferencesManager: PreferencesManager,
    private val multiCloudRepository: MultiCloudRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudTabUiState())
    val uiState: StateFlow<CloudTabUiState> = _uiState.asStateFlow()

    val gridColumns: StateFlow<Int> = preferencesManager.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    init {
        observeProviders()
        observeBackupRecords()
    }

    private fun observeProviders() {
        viewModelScope.launch {
            if (multiCloudRepository != null) {
                multiCloudRepository.providersInfoFlow.collect { providersList ->
                    val connected = providersList.filter { it.connectionState.isConnected }
                    _uiState.value = _uiState.value.copy(
                        isConnected = connected.isNotEmpty(),
                        connectedProviders = connected
                    )
                    if (connected.isNotEmpty()) {
                        loadCloudMedia(reset = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            items = emptyList(),
                            folders = emptyList(),
                            isLoading = false
                        )
                    }
                }
            } else {
                r2Repository.credentialsFlow.collect { creds ->
                    val connected = creds.isVerified && creds.secretAccessKey.isNotEmpty() && creds.bucketName.isNotEmpty()
                    _uiState.value = _uiState.value.copy(isConnected = connected)
                    if (connected) {
                        loadCloudMedia(reset = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            items = emptyList(),
                            folders = emptyList(),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun observeBackupRecords() {
        viewModelScope.launch {
            backupRepository.backupRecordsFlow.collect {
                if (_uiState.value.isConnected && !_uiState.value.isLoading) {
                    loadCloudMedia(reset = true, silent = true)
                }
            }
        }
    }

    fun setProviderFilter(providerId: String) {
        _uiState.value = _uiState.value.copy(
            providerFilter = providerId,
            currentPrefix = "",
            nextContinuationToken = null,
            items = emptyList()
        )
        loadCloudMedia(reset = true)
    }

    fun loadCloudMedia(reset: Boolean = true, silent: Boolean = false) {
        viewModelScope.launch {
            if (!_uiState.value.isConnected) return@launch

            if (reset) {
                if (!silent) {
                    _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                }
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            val prefix = if (_uiState.value.isFlattenFolders) "" else _uiState.value.currentPrefix
            val token = if (reset) null else _uiState.value.nextContinuationToken

            if (multiCloudRepository != null) {
                val filter = _uiState.value.providerFilter.takeIf { it != "all" }
                val result = multiCloudRepository.listMedia(
                    providerFilter = filter,
                    folderId = prefix.takeIf { it.isNotEmpty() },
                    continuationToken = token,
                    pageSize = 60
                )

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val mappedItems = page.items.map { it.toMediaItem() }
                    val newItems = if (reset) mappedItems else (_uiState.value.items + mappedItems).distinctBy { it.path }
                    val newFolders = if (reset) page.folders else (_uiState.value.folders + page.folders).distinct()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = newItems,
                        folders = newFolders,
                        nextContinuationToken = page.nextContinuationToken,
                        hasMore = page.isTruncated && !page.nextContinuationToken.isNullOrBlank(),
                        errorMessage = null,
                        isOffline = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to list cloud files"
                    )
                }
            } else {
                val result = r2Repository.listCloudMediaPage(
                    prefix = prefix,
                    continuationToken = token,
                    pageSize = 60
                )

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val newItems = if (reset) page.items else (_uiState.value.items + page.items).distinctBy { it.path }
                    val newFolders = if (reset) page.folders else (_uiState.value.folders + page.folders).distinct()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = newItems,
                        folders = newFolders,
                        nextContinuationToken = page.nextContinuationToken,
                        hasMore = page.isTruncated && !page.nextContinuationToken.isNullOrBlank(),
                        errorMessage = null,
                        isOffline = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to list cloud files"
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadCloudMedia(reset = true)
    }

    fun loadMore() {
        if (_uiState.value.hasMore && !_uiState.value.isLoading && !_uiState.value.isLoadingMore) {
            loadCloudMedia(reset = false)
        }
    }

    fun navigateToFolder(prefix: String) {
        _uiState.value = _uiState.value.copy(
            currentPrefix = prefix,
            isFlattenFolders = false,
            items = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false,
            selectedIds = emptySet(),
            isSelectionMode = false
        )
        loadCloudMedia(reset = true)
    }

    fun navigateUp() {
        val curr = _uiState.value.currentPrefix.trimEnd('/')
        if (curr.isEmpty()) return

        val parent = if (curr.contains('/')) {
            curr.substringBeforeLast('/') + "/"
        } else {
            ""
        }

        _uiState.value = _uiState.value.copy(
            currentPrefix = parent,
            items = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false,
            selectedIds = emptySet(),
            isSelectionMode = false
        )
        loadCloudMedia(reset = true)
    }

    fun toggleFlattenFolders() {
        val nextFlatten = !_uiState.value.isFlattenFolders
        _uiState.value = _uiState.value.copy(
            isFlattenFolders = nextFlatten,
            currentPrefix = if (nextFlatten) "" else _uiState.value.currentPrefix,
            items = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false
        )
        loadCloudMedia(reset = true)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
    }

    fun setGridColumns(cols: Int) {
        viewModelScope.launch {
            preferencesManager.setGridColumns(cols.coerceIn(2, 5))
        }
    }

    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedIds
        val newSelection = if (id in current) current - id else current + id
        _uiState.value = _uiState.value.copy(
            selectedIds = newSelection,
            isSelectionMode = newSelection.isNotEmpty()
        )
    }

    fun selectAll(items: List<MediaItem>) {
        _uiState.value = _uiState.value.copy(
            selectedIds = items.map { it.id }.toSet(),
            isSelectionMode = true
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun onItemDeletedLocally(item: MediaItem) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.filter { it.id != item.id && it.cloudKey != item.cloudKey },
            selectedIds = _uiState.value.selectedIds - item.id
        )
    }

    private fun resolveProviderId(item: MediaItem): String {
        return when (item.albumName?.lowercase()) {
            "google photos" -> "google_photos"
            "microsoft onedrive", "onedrive" -> "onedrive"
            "dropbox" -> "dropbox"
            else -> "r2"
        }
    }

    fun deleteItem(item: MediaItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val provId = resolveProviderId(item)
            val provider = multiCloudRepository?.getProvider(provId)
            if (provider != null && !provider.capabilities.canDelete) {
                val reason = provider.capabilities.getUnsupportedReason(CloudOperation.DELETE, provider.displayName)
                _uiState.value = _uiState.value.copy(errorMessage = reason)
                onComplete(false)
                return@launch
            }

            val key = item.cloudKey ?: item.path
            val result = if (multiCloudRepository != null) {
                multiCloudRepository.delete(provId, key)
            } else {
                r2Repository.deleteObject(key)
            }

            if (result.isSuccess) {
                onItemDeletedLocally(item)
                onComplete(true)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to delete item"
                _uiState.value = _uiState.value.copy(errorMessage = err)
                onComplete(false)
            }
        }
    }

    fun deleteSelected(items: List<MediaItem>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            var count = 0
            val idsToRemove = mutableSetOf<Long>()
            var blockedCount = 0

            for (item in items) {
                val provId = resolveProviderId(item)
                val provider = multiCloudRepository?.getProvider(provId)
                if (provider != null && !provider.capabilities.canDelete) {
                    blockedCount++
                    continue
                }

                val key = item.cloudKey ?: item.path
                val result = if (multiCloudRepository != null) {
                    multiCloudRepository.delete(provId, key)
                } else {
                    r2Repository.deleteObject(key)
                }

                if (result.isSuccess) {
                    count++
                    idsToRemove.add(item.id)
                }
            }

            if (blockedCount > 0) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "$blockedCount item(s) could not be deleted because Google Photos does not permit file deletion via API."
                )
            }

            _uiState.value = _uiState.value.copy(
                items = _uiState.value.items.filter { it.id !in idsToRemove },
                selectedIds = emptySet(),
                isSelectionMode = false
            )
            onComplete(count)
        }
    }

    fun setCategoryFilter(category: MediaCategory?) {
        _uiState.value = _uiState.value.copy(categoryFilter = category)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun getFilteredItems(): List<MediaItem> {
        val state = _uiState.value
        var list = state.items

        if (state.categoryFilter != null) {
            list = list.filter {
                MediaClassifier.classify(it.cloudKey ?: it.path, it.mimeType) == state.categoryFilter
            }
        }

        if (state.searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }

        return when (state.sortOrder) {
            SortOrder.DATE_DESC -> list.sortedByDescending { it.dateModified }
            SortOrder.DATE_ASC -> list.sortedBy { it.dateModified }
            SortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_DESC -> list.sortedByDescending { it.size }
        }
    }
}
