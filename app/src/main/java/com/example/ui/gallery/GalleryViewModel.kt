package com.example.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DeletionResult
import com.example.data.local.PreferencesManager
import com.example.data.local.SortOrder
import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.UploadResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GalleryTab {
    ALL,
    PHOTOS,
    VIDEOS,
    FAVORITES,
    ALBUMS,
    COLLECTIONS,
    CLOUD
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val mediaItems: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedTab: GalleryTab = GalleryTab.ALL,
    val activeAlbumName: String? = null,
    val searchQuery: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val backedUpIds: Set<Long> = emptySet(),
    val deletionResult: DeletionResult? = null,
    val infoMessage: String? = null,
    val isBatchUploading: Boolean = false,
    val batchUploadProgress: Pair<Int, Int>? = null // (current, total)
)

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val backupRepository: BackupRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val gridColumns: StateFlow<Int> = preferencesManager.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val sortOrder: StateFlow<SortOrder> = preferencesManager.sortOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.DATE_DESC)

    init {
        loadMedia()
        observeBackupRecords()
        observeMediaStoreChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeMediaStoreChanges() {
        viewModelScope.launch {
            mediaRepository.observeMediaChanges()
                .debounce(400L)
                .collect {
                    refreshMedia(silent = true)
                }
        }
    }

    private fun observeBackupRecords() {
        viewModelScope.launch {
            backupRepository.backupRecordsFlow.collect { records ->
                val backedIds = records
                    .filter { it.status == "COMPLETED" }
                    .map { it.mediaStoreId }
                    .toSet()
                _uiState.value = _uiState.value.copy(backedUpIds = backedIds)
            }
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            if (_uiState.value.mediaItems.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                val items = mediaRepository.loadMediaItems()
                val albums = mediaRepository.getAlbums(items)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    mediaItems = items,
                    albums = albums
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    infoMessage = "Failed to load media: ${e.message}"
                )
            }
        }
    }

    fun refreshMedia(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            }
            try {
                val items = mediaRepository.loadMediaItems()
                val albums = mediaRepository.getAlbums(items)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    isLoading = false,
                    mediaItems = items,
                    albums = albums
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    isLoading = false,
                    infoMessage = "Failed to refresh media: ${e.message}"
                )
            }
        }
    }

    fun selectTab(tab: GalleryTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            activeAlbumName = null
        )
    }

    fun selectAlbum(albumName: String?) {
        _uiState.value = _uiState.value.copy(
            activeAlbumName = albumName
        )
    }

    fun clearActiveAlbum() {
        _uiState.value = _uiState.value.copy(
            activeAlbumName = null
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setGridColumns(cols: Int) {
        viewModelScope.launch {
            preferencesManager.setGridColumns(cols.coerceIn(2, 5))
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            preferencesManager.setSortOrder(order)
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

    fun toggleSelectionMode() {
        val next = !_uiState.value.isSelectionMode
        _uiState.value = _uiState.value.copy(
            isSelectionMode = next,
            selectedIds = if (!next) emptySet() else _uiState.value.selectedIds
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

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            mediaRepository.toggleFavorite(id)
            loadMedia()
        }
    }

    fun deleteSelected(itemsToDelete: List<MediaItem>) {
        viewModelScope.launch {
            val result = mediaRepository.deleteMedia(itemsToDelete)
            _uiState.value = _uiState.value.copy(deletionResult = result)
            if (result is DeletionResult.Success) {
                clearSelection()
                loadMedia()
            }
        }
    }

    fun onDeletionConfirmed() {
        _uiState.value = _uiState.value.copy(deletionResult = null)
        clearSelection()
        loadMedia()
    }

    fun clearDeletionResult() {
        _uiState.value = _uiState.value.copy(deletionResult = null)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun backupSelected(items: List<MediaItem>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBatchUploading = true,
                batchUploadProgress = Pair(0, items.size)
            )

            var uploaded = 0
            for ((index, item) in items.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    batchUploadProgress = Pair(index + 1, items.size)
                )
                val res = backupRepository.uploadSingleMedia(item)
                if (res is UploadResult.Success || res is UploadResult.AlreadyBackedUp) {
                    uploaded++
                }
            }

            _uiState.value = _uiState.value.copy(
                isBatchUploading = false,
                batchUploadProgress = null,
                infoMessage = "Saved $uploaded of ${items.size} to Cloudflare R2"
            )
            clearSelection()
        }
    }

    fun getFilteredItems(currentSortOrder: SortOrder): List<MediaItem> {
        val state = _uiState.value
        var list = state.mediaItems

        // Filter by Album if selected
        if (state.activeAlbumName != null) {
            list = list.filter { it.albumName.equals(state.activeAlbumName, ignoreCase = true) }
        } else {
            // Filter by Tab
            list = when (state.selectedTab) {
                GalleryTab.ALL -> list
                GalleryTab.PHOTOS -> list.filter { !it.isVideo }
                GalleryTab.VIDEOS -> list.filter { it.isVideo }
                GalleryTab.FAVORITES -> list.filter { it.isFavorite }
                GalleryTab.ALBUMS -> list
                GalleryTab.COLLECTIONS -> emptyList()
                GalleryTab.CLOUD -> emptyList()
            }
        }

        // Search Query Filter
        if (state.searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }

        // Sort
        return when (currentSortOrder) {
            SortOrder.DATE_DESC -> list.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_DESC -> list.sortedByDescending { it.size }
        }
    }
}
