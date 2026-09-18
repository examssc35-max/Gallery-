package com.example.ui.cloud

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.SortOrder
import com.example.domain.model.CloudFileType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaClassifier
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class CloudTabUiState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val r2Files: List<R2Item> = emptyList(),
    val folders: List<String> = emptyList(),
    val currentPrefix: String = "",
    val searchQuery: String = "",
    val categoryFilter: MediaCategory? = null,
    val fileTypeFilter: CloudFileType? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val selectedKeys: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val errorMessage: String? = null,
    val nextContinuationToken: String? = null,
    val hasMore: Boolean = false,
    val isGridView: Boolean = true,
    val selectedItemForDetails: R2Item? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isBatchDownloading: Boolean = false,
    val batchDownloadProgress: Pair<Int, Int>? = null
)

class CloudTabViewModel(
    private val r2Repository: R2Repository,
    private val backupRepository: BackupRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val httpClient = OkHttpClient.Builder().build()

    private val _uiState = MutableStateFlow(CloudTabUiState())
    val uiState: StateFlow<CloudTabUiState> = _uiState.asStateFlow()

    val gridColumns: StateFlow<Int> = preferencesManager.gridColumnsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    init {
        observeR2Credentials()
        observeBackupRecords()
    }

    private fun observeR2Credentials() {
        viewModelScope.launch {
            r2Repository.credentialsFlow.collect { creds ->
                val connected = creds.secretAccessKey.isNotEmpty() && creds.bucketName.isNotEmpty()
                _uiState.value = _uiState.value.copy(isConnected = connected)
                if (connected) {
                    loadCloudMedia(reset = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        items = emptyList(),
                        r2Files = emptyList(),
                        folders = emptyList(),
                        isLoading = false
                    )
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

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    fun setFileTypeFilter(type: CloudFileType?) {
        if (_uiState.value.fileTypeFilter == type) return
        _uiState.value = _uiState.value.copy(
            fileTypeFilter = type,
            r2Files = emptyList(),
            items = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false,
            isLoading = true,
            errorMessage = null,
            selectedKeys = emptySet(),
            isSelectionMode = false
        )
        loadCloudMedia(reset = true)
    }

    fun setCategoryFilter(category: MediaCategory?) {
        _uiState.value = _uiState.value.copy(categoryFilter = category)
    }

    fun openItemDetails(item: R2Item) {
        _uiState.value = _uiState.value.copy(selectedItemForDetails = item)
    }

    fun closeItemDetails() {
        _uiState.value = _uiState.value.copy(selectedItemForDetails = null)
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

            try {
                val filter = _uiState.value.fileTypeFilter
                val token = if (reset) null else _uiState.value.nextContinuationToken

                val result = if (filter != null) {
                    r2Repository.listGlobalMediaPage(
                        fileType = filter,
                        continuationToken = token,
                        targetPageSize = 60
                    )
                } else {
                    r2Repository.listFolderPage(
                        prefix = _uiState.value.currentPrefix,
                        continuationToken = token,
                        pageSize = 60
                    )
                }

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    val newFiles = if (reset) page.r2Items else (_uiState.value.r2Files + page.r2Items).distinctBy { it.key }
                    val newItems = if (reset) page.items else (_uiState.value.items + page.items).distinctBy { it.path }
                    val newFolders = if (reset) page.folders else (_uiState.value.folders + page.folders).distinct()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        r2Files = newFiles,
                        items = newItems,
                        folders = newFolders,
                        nextContinuationToken = page.nextContinuationToken,
                        hasMore = page.isTruncated && !page.nextContinuationToken.isNullOrBlank(),
                        errorMessage = null
                    )
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage
                        ?: result.exceptionOrNull()?.message
                        ?: "Failed to load files from Cloudflare R2"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = errorMsg
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = e.localizedMessage ?: e.message ?: "An unexpected error occurred"
                )
            } finally {
                if (_uiState.value.isLoading || _uiState.value.isRefreshing || _uiState.value.isLoadingMore) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadCloudMedia(reset = true, silent = true)
    }

    fun loadMore() {
        if (_uiState.value.hasMore && !_uiState.value.isLoading && !_uiState.value.isLoadingMore) {
            loadCloudMedia(reset = false)
        }
    }

    fun navigateToFolder(prefix: String) {
        val normalized = if (prefix.isNotEmpty() && !prefix.endsWith("/")) "$prefix/" else prefix
        _uiState.value = _uiState.value.copy(
            currentPrefix = normalized,
            fileTypeFilter = null,
            items = emptyList(),
            r2Files = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false,
            selectedKeys = emptySet(),
            isSelectionMode = false,
            isLoading = true,
            errorMessage = null
        )
        loadCloudMedia(reset = true)
    }

    fun navigateUp() {
        val curr = _uiState.value.currentPrefix.trimEnd('/')
        if (curr.isEmpty() && _uiState.value.fileTypeFilter == null) return

        if (_uiState.value.fileTypeFilter != null) {
            setFileTypeFilter(null)
            return
        }

        val parent = if (curr.contains('/')) {
            curr.substringBeforeLast('/') + "/"
        } else {
            ""
        }

        _uiState.value = _uiState.value.copy(
            currentPrefix = parent,
            fileTypeFilter = null,
            items = emptyList(),
            r2Files = emptyList(),
            folders = emptyList(),
            nextContinuationToken = null,
            hasMore = false,
            selectedKeys = emptySet(),
            isSelectionMode = false,
            isLoading = true,
            errorMessage = null
        )
        loadCloudMedia(reset = true)
    }

    fun getBreadcrumbs(): List<Pair<String, String>> {
        val prefix = _uiState.value.currentPrefix.trim('/')
        if (prefix.isEmpty()) return listOf("Bucket Root" to "")
        val parts = prefix.split('/')
        val result = mutableListOf("Bucket Root" to "")
        var accumulated = ""
        for (part in parts) {
            accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
            result.add(part to "$accumulated/")
        }
        return result
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

    fun toggleSelection(key: String) {
        val current = _uiState.value.selectedKeys
        val newSelection = if (key in current) current - key else current + key
        _uiState.value = _uiState.value.copy(
            selectedKeys = newSelection,
            isSelectionMode = newSelection.isNotEmpty()
        )
    }

    fun selectAll(files: List<R2Item>) {
        _uiState.value = _uiState.value.copy(
            selectedKeys = files.filter { !it.isFolder }.map { it.key }.toSet(),
            isSelectionMode = true
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedKeys = emptySet(),
            isSelectionMode = false
        )
    }

    fun onItemDeletedLocally(item: MediaItem) {
        val key = item.cloudKey ?: item.path
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.filter { it.id != item.id && it.cloudKey != key },
            r2Files = _uiState.value.r2Files.filter { it.key != key },
            selectedKeys = _uiState.value.selectedKeys - key
        )
    }

    fun deleteSingleItem(item: R2Item, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = r2Repository.deleteObject(item.key)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    r2Files = _uiState.value.r2Files.filter { it.key != item.key },
                    items = _uiState.value.items.filter { (it.cloudKey ?: it.path) != item.key },
                    selectedKeys = _uiState.value.selectedKeys - item.key
                )
                onComplete(true)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to delete item from R2"
                _uiState.value = _uiState.value.copy(errorMessage = err)
                onComplete(false)
            }
        }
    }

    fun deleteSelected(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val keys = _uiState.value.selectedKeys.toList()
            var count = 0
            val keysToRemove = mutableSetOf<String>()

            for (key in keys) {
                val result = r2Repository.deleteObject(key)
                if (result.isSuccess) {
                    count++
                    keysToRemove.add(key)
                }
            }

            _uiState.value = _uiState.value.copy(
                r2Files = _uiState.value.r2Files.filter { it.key !in keysToRemove },
                items = _uiState.value.items.filter { (it.cloudKey ?: it.path) !in keysToRemove },
                selectedKeys = emptySet(),
                isSelectionMode = false
            )
            onComplete(count)
        }
    }

    fun createFolder(folderName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val cleanName = folderName.trim().trim('/')
            if (cleanName.isEmpty()) {
                onResult(false, "Folder name cannot be empty")
                return@launch
            }
            val prefix = _uiState.value.currentPrefix
            val folderKey = if (prefix.isEmpty()) "$cleanName/" else "${prefix.trimEnd('/')}/$cleanName/"

            val result = r2Repository.createFolder(folderKey)
            if (result.isSuccess) {
                loadCloudMedia(reset = true, silent = true)
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to create folder")
            }
        }
    }

    fun uploadFileFromUri(context: Context, uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "upload_${System.currentTimeMillis()}"
                var size = -1L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) fileName = name
                        }
                        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open input stream for file")

                val totalSize = if (size > 0) size else inputStream.available().toLong()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploading = true, uploadProgress = 0f)
                }

                val uploadResult = r2Repository.uploadFile(
                    fileName = fileName,
                    prefix = _uiState.value.currentPrefix,
                    inputStream = inputStream,
                    contentLength = totalSize,
                    mimeType = mimeType,
                    onProgress = { bytes, total ->
                        val prog = if (total > 0) bytes.toFloat() / total.toFloat() else 0f
                        _uiState.value = _uiState.value.copy(uploadProgress = prog)
                    }
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploading = false, uploadProgress = 0f)
                    if (uploadResult.isSuccess) {
                        loadCloudMedia(reset = true, silent = true)
                        onResult(true, null)
                    } else {
                        onResult(false, uploadResult.exceptionOrNull()?.message ?: "Upload failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isUploading = false, uploadProgress = 0f)
                    onResult(false, e.localizedMessage ?: "Failed to upload file")
                }
            }
        }
    }

    fun downloadItem(
        context: Context,
        item: R2Item,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val presignedUrl = item.downloadUrl ?: try {
                r2Repository.getPresignedUrl(item.key).getOrNull()
            } catch (_: Exception) {
                null
            }

            if (presignedUrl.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "No download URL available for ${item.name}")
                }
                return@launch
            }

            try {
                val req = Request.Builder().url(presignedUrl).build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Download failed with HTTP ${resp.code}")
                    }
                    return@launch
                }

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val destFile = File(downloadDir, item.name)
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(item.mimeType.ifEmpty { null }),
                    null
                )

                withContext(Dispatchers.Main) {
                    onResult(true, destFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Download failed")
                }
            }
        }
    }

    fun downloadSelected(context: Context, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val selectedItems = _uiState.value.r2Files.filter { it.key in _uiState.value.selectedKeys && !it.isFolder }
            if (selectedItems.isEmpty()) {
                onComplete(0)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isBatchDownloading = true,
                batchDownloadProgress = 0 to selectedItems.size
            )

            var successCount = 0
            for ((index, item) in selectedItems.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    batchDownloadProgress = (index + 1) to selectedItems.size
                )
                downloadItem(context, item) { ok, _ ->
                    if (ok) successCount++
                }
            }

            _uiState.value = _uiState.value.copy(
                isBatchDownloading = false,
                batchDownloadProgress = null,
                selectedKeys = emptySet(),
                isSelectionMode = false
            )
            onComplete(successCount)
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun getFilteredFiles(): List<R2Item> {
        val state = _uiState.value
        var list = state.r2Files

        // File type filter:
        // When activeFilter is null ("All"): show all objects (folders + files) in the current prefix.
        // When a specific filter is selected (Photos, Videos, Audio, Documents, Archives, Other):
        // do NOT include folders. Only display objects matching that fileType in the current prefix.
        if (state.fileTypeFilter != null) {
            list = list.filter { !it.isFolder && it.fileType == state.fileTypeFilter }
        }

        // Category filter if active
        if (state.categoryFilter != null) {
            list = list.filter {
                it.isFolder || MediaClassifier.classify(it.key, it.mimeType) == state.categoryFilter
            }
        }

        // Search query
        if (state.searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }

        // Keep folders first (only present when "All" is active), then sort files
        val folders = list.filter { it.isFolder }.sortedBy { it.name.lowercase() }
        val files = list.filter { !it.isFolder }

        val sortedFiles = when (state.sortOrder) {
            SortOrder.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOrder.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOrder.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_DESC -> files.sortedByDescending { it.size }
        }

        return folders + sortedFiles
    }

    fun getFilteredMediaItems(): List<MediaItem> {
        val state = _uiState.value
        var list = state.items

        // File type filter
        if (state.fileTypeFilter != null) {
            list = when (state.fileTypeFilter) {
                CloudFileType.IMAGE -> list.filter { !it.isVideo }
                CloudFileType.VIDEO -> list.filter { it.isVideo }
                else -> list
            }
        }

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
