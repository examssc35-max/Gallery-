package com.example.ui.cloud

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.SortOrder
import com.example.domain.model.MediaItem
import com.example.domain.model.multicloud.CloudFileType
import com.example.domain.model.multicloud.CloudFileTypeResolver
import com.example.domain.model.multicloud.CloudMediaItem
import com.example.domain.repository.R2Repository
import com.example.ui.gallery.components.MediaGridItem
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudTabScreen(
    viewModel: CloudTabViewModel,
    r2Repository: R2Repository,
    onOpenViewer: (initialIndex: Int, items: List<MediaItem>) -> Unit,
    onConnectR2: () -> Unit,
    onNavigateToStorageUsage: () -> Unit = {},
    onNavigateToConnectedServices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isBatchDownloading by remember { mutableStateOf(false) }
    var batchDownloadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val filteredItems = remember(uiState) { viewModel.getFilteredItems() }
    val filteredCloudFiles = remember(uiState) { viewModel.getFilteredCloudFiles() }
    val gridState = rememberLazyGridState()

    // Detect when user scrolled to bottom to trigger loadMore
    LaunchedEffect(gridState, uiState.hasMore, uiState.isLoadingMore) {
        snapshotFlow {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 6
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && uiState.hasMore && !uiState.isLoading && !uiState.isLoadingMore) {
                viewModel.loadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search / Filter / Folder Navigation Bar
            CloudSubHeader(
                uiState = uiState,
                isSearchActive = isSearchActive,
                onSearchActiveChange = { isSearchActive = it },
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onNavigateUp = { viewModel.navigateUp() },
                onRefresh = { viewModel.refresh() },
                onToggleFlatten = { viewModel.toggleFlattenFolders() },
                onToggleViewMode = { viewModel.toggleViewMode() },
                onFileTypeFilterChange = { viewModel.setFileTypeFilter(it) },
                breadcrumbs = viewModel.getBreadcrumbs(),
                onBreadcrumbClick = { viewModel.navigateToFolder(it) },
                onShowSortMenu = { showSortMenu = true },
                onNavigateToStorageUsage = onNavigateToStorageUsage,
                onNavigateToConnectedServices = onNavigateToConnectedServices,
                onClearCategoryFilter = { viewModel.setCategoryFilter(null) },
                onProviderFilterChange = { viewModel.setProviderFilter(it) }
            )

            // Sort Menu Dropdown
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Date Modified (Newest First) ${if (uiState.sortOrder == SortOrder.DATE_DESC) "✓" else ""}") },
                    onClick = {
                        viewModel.setSortOrder(SortOrder.DATE_DESC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Date Modified (Oldest First) ${if (uiState.sortOrder == SortOrder.DATE_ASC) "✓" else ""}") },
                    onClick = {
                        viewModel.setSortOrder(SortOrder.DATE_ASC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Name (A to Z) ${if (uiState.sortOrder == SortOrder.NAME_ASC) "✓" else ""}") },
                    onClick = {
                        viewModel.setSortOrder(SortOrder.NAME_ASC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Name (Z to A) ${if (uiState.sortOrder == SortOrder.NAME_DESC) "✓" else ""}") },
                    onClick = {
                        viewModel.setSortOrder(SortOrder.NAME_DESC)
                        showSortMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Size (Largest First) ${if (uiState.sortOrder == SortOrder.SIZE_DESC) "✓" else ""}") },
                    onClick = {
                        viewModel.setSortOrder(SortOrder.SIZE_DESC)
                        showSortMenu = false
                    }
                )
            }

            // Batch download progress indicator
            if (isBatchDownloading && batchDownloadProgress != null) {
                val (current, total) = batchDownloadProgress!!
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "Downloading files: $current / $total items",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Main State Switcher
            when {
                // 1. Not connected state
                !uiState.isConnected -> {
                    CloudNotConnectedState(
                        onConnectClick = onNavigateToConnectedServices
                    )
                }

                // 2. Loading initial data
                uiState.isLoading && uiState.items.isEmpty() && uiState.cloudFiles.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading cloud media files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Connected but bucket/cloud is empty (No items, no cloud files, and no folders)
                filteredItems.isEmpty() && filteredCloudFiles.isEmpty() && uiState.folders.isEmpty() -> {
                    CloudEmptyBucketState(
                        searchQuery = uiState.searchQuery,
                        currentPrefix = uiState.currentPrefix,
                        onRefresh = { viewModel.refresh() }
                    )
                }

                // 4. Multi-Cloud Files Browser (Unified files or Photos fallback)
                filteredCloudFiles.isNotEmpty() -> {
                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.isGridView) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(gridColumns),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Folder Chips row at top if folders exist and not in flatten mode
                                if (uiState.folders.isNotEmpty() && !uiState.isFlattenFolders) {
                                    item(span = { GridItemSpan(gridColumns) }) {
                                        FolderChipsRow(
                                            folders = uiState.folders,
                                            currentPrefix = uiState.currentPrefix,
                                            onFolderClick = { folder ->
                                                viewModel.navigateToFolder(folder)
                                            }
                                        )
                                    }
                                }

                                itemsIndexed(
                                    items = filteredCloudFiles,
                                    key = { _, file -> file.remoteId.ifEmpty { file.folderPath ?: file.name } }
                                ) { _, file ->
                                    val mediaItem = remember(file) { file.toMediaItem() }
                                    val fileId = mediaItem.id
                                    if (file.isFolder) {
                                        CloudGridFolderCard(
                                            file = file,
                                            onClick = { viewModel.navigateToFolder(file.folderPath ?: file.name) }
                                        )
                                    } else if (file.isMedia) {
                                        Box {
                                            MediaGridItem(
                                                item = mediaItem,
                                                isSelected = fileId in uiState.selectedIds,
                                                isSelectionMode = uiState.isSelectionMode,
                                                isBackedUp = true,
                                                onClick = {
                                                    if (uiState.isSelectionMode) {
                                                        viewModel.toggleSelection(fileId)
                                                    } else {
                                                        val idx = filteredItems.indexOfFirst { it.id == mediaItem.id || it.cloudKey == file.remoteId }
                                                        if (idx >= 0) {
                                                            onOpenViewer(idx, filteredItems)
                                                        } else {
                                                            onOpenViewer(0, listOf(mediaItem))
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    viewModel.toggleSelection(fileId)
                                                }
                                            )

                                            // Provider Pill Badge
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = file.providerName,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        CloudGridFileCard(
                                            file = file,
                                            isSelected = fileId in uiState.selectedIds,
                                            isSelectionMode = uiState.isSelectionMode,
                                            onClick = {
                                                if (uiState.isSelectionMode) {
                                                    viewModel.toggleSelection(fileId)
                                                } else {
                                                    viewModel.openFileDetails(file)
                                                }
                                            },
                                            onLongClick = {
                                                viewModel.toggleSelection(fileId)
                                            }
                                        )
                                    }
                                }

                                if (uiState.isLoadingMore) {
                                    item(span = { GridItemSpan(gridColumns) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        } else {
                            // List View
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                if (uiState.folders.isNotEmpty() && !uiState.isFlattenFolders) {
                                    item {
                                        FolderChipsRow(
                                            folders = uiState.folders,
                                            currentPrefix = uiState.currentPrefix,
                                            onFolderClick = { folder ->
                                                viewModel.navigateToFolder(folder)
                                            }
                                        )
                                    }
                                }

                                items(
                                    items = filteredCloudFiles,
                                    key = { file -> file.remoteId.ifEmpty { file.folderPath ?: file.name } }
                                ) { file ->
                                    val mediaItem = remember(file) { file.toMediaItem() }
                                    val fileId = mediaItem.id
                                    if (file.isFolder) {
                                        CloudListFolderRow(
                                            folder = file.name,
                                            currentPrefix = uiState.currentPrefix,
                                            onClick = { viewModel.navigateToFolder(file.folderPath ?: file.name) }
                                        )
                                    } else {
                                        CloudListFileRow(
                                            file = file,
                                            isSelected = fileId in uiState.selectedIds,
                                            isSelectionMode = uiState.isSelectionMode,
                                            onClick = {
                                                if (uiState.isSelectionMode) {
                                                    viewModel.toggleSelection(fileId)
                                                } else if (file.isMedia) {
                                                    val idx = filteredItems.indexOfFirst { it.id == mediaItem.id || it.cloudKey == file.remoteId }
                                                    if (idx >= 0) {
                                                        onOpenViewer(idx, filteredItems)
                                                    } else {
                                                        onOpenViewer(0, listOf(mediaItem))
                                                    }
                                                } else {
                                                    viewModel.openFileDetails(file)
                                                }
                                            },
                                            onLongClick = {
                                                viewModel.toggleSelection(fileId)
                                            },
                                            onInfoClick = {
                                                viewModel.openFileDetails(file)
                                            },
                                            onDownloadClick = {
                                                viewModel.downloadFile(context, file) { ok, path ->
                                                    scope.launch {
                                                        if (ok) snackbarHostState.showSnackbar("Downloaded to $path")
                                                        else snackbarHostState.showSnackbar("Download failed")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                if (uiState.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Populated bucket: Gallery Grid (Legacy R2 items fallback)
                else -> {
                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Folder Chips row at top if folders exist and not in flatten mode
                            if (uiState.folders.isNotEmpty() && !uiState.isFlattenFolders) {
                                item(span = { GridItemSpan(gridColumns) }) {
                                    FolderChipsRow(
                                        folders = uiState.folders,
                                        currentPrefix = uiState.currentPrefix,
                                        onFolderClick = { folder ->
                                            viewModel.navigateToFolder(folder)
                                        }
                                    )
                                }
                            }

                            // Media Items with Provider Source Badges
                            itemsIndexed(
                                items = filteredItems,
                                key = { _, item -> item.cloudKey ?: item.path }
                            ) { index, item ->
                                Box {
                                    MediaGridItem(
                                        item = item,
                                        isSelected = item.id in uiState.selectedIds,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isBackedUp = true,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                onOpenViewer(index, filteredItems)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleSelection(item.id)
                                        }
                                    )

                                    // Provider Pill Badge
                                    val badgeLabel = when (item.albumName?.lowercase()) {
                                        "google photos" -> "Photos"
                                        "microsoft onedrive", "onedrive" -> "OneDrive"
                                        "dropbox" -> "Dropbox"
                                        else -> "R2"
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Loading more indicator at the bottom
                            if (uiState.isLoadingMore) {
                                item(span = { GridItemSpan(gridColumns) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection Action Bar at the bottom
        AnimatedVisibility(
            visible = uiState.isSelectionMode && uiState.selectedIds.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                        Text(
                            text = "${uiState.selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.selectAll(filteredItems) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }

                        // Batch Download
                        IconButton(
                            onClick = {
                                val selectedItems = filteredItems.filter { it.id in uiState.selectedIds }
                                isBatchDownloading = true
                                batchDownloadProgress = Pair(0, selectedItems.size)

                                scope.launch {
                                    var downloaded = 0
                                    for ((idx, item) in selectedItems.withIndex()) {
                                        val key = item.cloudKey ?: item.path
                                        val fileName = key.substringAfterLast('/')
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val destFile = File(downloadsDir, fileName)

                                        val res = r2Repository.downloadKeyToFile(key, destFile)
                                        if (res.isSuccess) {
                                            downloaded++
                                            MediaScannerConnection.scanFile(
                                                context,
                                                arrayOf(destFile.absolutePath),
                                                arrayOf(item.mimeType),
                                                null
                                            )
                                        }
                                        batchDownloadProgress = Pair(idx + 1, selectedItems.size)
                                    }
                                    isBatchDownloading = false
                                    batchDownloadProgress = null
                                    viewModel.clearSelection()
                                    snackbarHostState.showSnackbar("Downloaded $downloaded files to Downloads folder")
                                }
                            },
                            enabled = !isBatchDownloading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download Selected")
                        }

                        // Batch Delete
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
        )
    }

    // Delete Confirmation Dialog for batch delete
    if (showDeleteConfirmDialog) {
        val selectedItems = filteredItems.filter { it.id in uiState.selectedIds }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete from Cloud") },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedItems.size} file(s) from cloud storage? Providers that support deletion will remove the file(s). This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelected(selectedItems) { deletedCount ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Deleted $deletedCount files from cloud storage")
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error or capability notification dialog
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Cloud Storage Notice") },
            text = { Text(uiState.errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // Cloud File Details Modal Dialog
    if (uiState.selectedFileForDetails != null) {
        CloudFileDetailsDialog(
            file = uiState.selectedFileForDetails!!,
            onDismiss = { viewModel.closeFileDetails() },
            onDownload = { fileToDownload ->
                viewModel.downloadFile(context, fileToDownload) { success, resultPath ->
                    viewModel.closeFileDetails()
                    scope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Downloaded to $resultPath")
                        } else {
                            snackbarHostState.showSnackbar("Download failed: ${resultPath ?: "Unknown error"}")
                        }
                    }
                }
            },
            onOpenMedia = { fileToOpen ->
                viewModel.closeFileDetails()
                val media = fileToOpen.toMediaItem()
                val idx = filteredItems.indexOfFirst { it.id == media.id || it.cloudKey == fileToOpen.remoteId }
                if (idx >= 0) {
                    onOpenViewer(idx, filteredItems)
                } else {
                    onOpenViewer(0, listOf(media))
                }
            }
        )
    }
}

@Composable
private fun CloudSubHeader(
    uiState: CloudTabUiState,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFlatten: () -> Unit,
    onToggleViewMode: () -> Unit,
    onFileTypeFilterChange: (CloudFileType?) -> Unit,
    breadcrumbs: List<Pair<String, String>>,
    onBreadcrumbClick: (String) -> Unit,
    onShowSortMenu: () -> Unit,
    onNavigateToStorageUsage: () -> Unit = {},
    onNavigateToConnectedServices: () -> Unit = {},
    onClearCategoryFilter: () -> Unit = {},
    onProviderFilterChange: (String) -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Offline banner if applicable
            if (uiState.isOffline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You're offline. Cloud changes can't be synchronized right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search cloud files...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                onSearchQueryChange("")
                                onSearchActiveChange(false)
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Breadcrumbs / Prefix indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.currentPrefix.isNotEmpty() && !uiState.isFlattenFolders) {
                            IconButton(
                                onClick = onNavigateUp,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up a folder",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Icon(
                            imageVector = if (uiState.isFlattenFolders) Icons.Default.Cloud else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isFlattenFolders) {
                                "All Cloud Files (Unified)"
                            } else if (uiState.currentPrefix.isEmpty()) {
                                "Root /"
                            } else {
                                uiState.currentPrefix
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSearchActiveChange(true) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = onShowSortMenu,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = onToggleViewMode,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isGridView) Icons.Default.FormatListBulleted else Icons.Default.GridView,
                                contentDescription = if (uiState.isGridView) "Switch to List View" else "Switch to Grid View",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleFlatten,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isFlattenFolders) Icons.Default.Folder else Icons.Default.Cloud,
                                contentDescription = if (uiState.isFlattenFolders) "Hierarchy View" else "Unified Flatten View",
                                tint = if (uiState.isFlattenFolders) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToStorageUsage,
                            modifier = Modifier.size(36.dp).testTag("cloud_storage_usage_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = "Storage Usage",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToConnectedServices,
                            modifier = Modifier.size(36.dp).testTag("connected_services_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Connected Services",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Interactive Breadcrumb Trail Bar (when not in flattened mode and nested)
            if (!uiState.isFlattenFolders && breadcrumbs.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(breadcrumbs.size) { idx ->
                        val item = breadcrumbs[idx]
                        val name = item.first
                        val prefix = item.second
                        val isLast = idx == breadcrumbs.size - 1
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onBreadcrumbClick(prefix) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Multi-Cloud Provider Filter Chips Row
            if (uiState.connectedProviders.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = uiState.providerFilter == "all",
                            onClick = { onProviderFilterChange("all") },
                            label = { Text("All (${uiState.items.size})", fontSize = 12.sp) }
                        )
                    }
                    items(uiState.connectedProviders, key = { it.providerId }) { prov ->
                        FilterChip(
                            selected = uiState.providerFilter == prov.providerId,
                            onClick = { onProviderFilterChange(prov.providerId) },
                            label = { Text(prov.displayName, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // File Type Filter Chips Row
            if (uiState.cloudFiles.isNotEmpty() || uiState.connectedProviders.isNotEmpty()) {
                val fileTypes = listOf(
                    null to "All",
                    CloudFileType.IMAGE to "Photos",
                    CloudFileType.VIDEO to "Videos",
                    CloudFileType.DOCUMENT to "Docs",
                    CloudFileType.AUDIO to "Audio",
                    CloudFileType.ARCHIVE to "Archives",
                    CloudFileType.APK to "APKs",
                    CloudFileType.TEXT to "Text",
                    CloudFileType.OTHER to "Other"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(fileTypes) { (type, label) ->
                        FilterChip(
                            selected = uiState.fileTypeFilter == type,
                            onClick = { onFileTypeFilterChange(type) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Category Filter Active Banner
            if (uiState.categoryFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = onClearCategoryFilter,
                        label = {
                            Text(
                                text = "Filtered: ${uiState.categoryFilter.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear category filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderChipsRow(
    folders: List<String>,
    currentPrefix: String,
    onFolderClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folderKey ->
                val folderName = folderKey.removePrefix(currentPrefix).trimEnd('/')
                ElevatedAssistChip(
                    onClick = { onFolderClick(folderKey) },
                    label = { Text(folderName, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CloudNotConnectedState(onConnectClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Cloud Storage Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect Cloudflare R2, Google Photos, Microsoft OneDrive, or Dropbox to browse and back up your photos and videos safely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConnectClick,
                modifier = Modifier.testTag("connect_cloud_storage_button")
            ) {
                Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Cloud Storage")
            }
        }
    }
}

@Composable
private fun CloudEmptyBucketState(
    searchQuery: String,
    currentPrefix: String,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "No items match '$searchQuery'"
                } else if (currentPrefix.isNotEmpty()) {
                    "This folder is empty."
                } else {
                    "No cloud files found."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "Try checking your spelling or clearing the search query."
                } else {
                    "Files backed up or stored in your connected cloud services will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Cloud Media")
            }
        }
    }
}

@Composable
private fun CloudGridFolderCard(
    file: CloudMediaItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudGridFileCard(
    file: CloudMediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val iconColor = CloudFileTypeResolver.getIconColor(file.fileType)
    val iconVector = CloudFileTypeResolver.getIcon(file.fileType)

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                if (file.size > 0) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Top-left provider badge
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = file.providerName,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Selection check indicator
            if (isSelectionMode) {
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudListFolderRow(
    folder: String,
    currentPrefix: String,
    onClick: () -> Unit
) {
    val folderName = folder.removePrefix(currentPrefix).trimEnd('/')
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CloudListFileRow(
    file: CloudMediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val iconColor = CloudFileTypeResolver.getIconColor(file.fileType)
    val iconVector = CloudFileTypeResolver.getIcon(file.fileType)

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or file icon
            if (file.isMedia && (!file.thumbnailUrl.isNullOrEmpty() || !file.downloadUrl.isNullOrEmpty())) {
                AsyncImage(
                    model = file.thumbnailUrl ?: file.downloadUrl,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val detailsText = buildString {
                    if (file.size > 0) append(formatFileSize(file.size))
                    if (isNotEmpty()) append(" • ")
                    append(file.providerName)
                    if (file.modifiedAt > 0) {
                        append(" • ")
                        append(formatDate(file.modifiedAt))
                    }
                }

                Text(
                    text = detailsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Trailing actions
            if (!isSelectionMode) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download ${file.name}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "File Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                    shape = CircleShape,
                    modifier = Modifier.size(22.dp)
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudFileDetailsDialog(
    file: CloudMediaItem,
    onDismiss: () -> Unit,
    onDownload: (CloudMediaItem) -> Unit,
    onOpenMedia: (CloudMediaItem) -> Unit
) {
    val iconColor = CloudFileTypeResolver.getIconColor(file.fileType)
    val iconVector = CloudFileTypeResolver.getIcon(file.fileType)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Source Provider", value = file.providerName)
                DetailRow(label = "Category", value = file.fileType.displayName)
                if (file.mimeType.isNotEmpty()) {
                    DetailRow(label = "MIME Type", value = file.mimeType)
                }
                if (file.size > 0) {
                    DetailRow(label = "Size", value = formatFileSize(file.size))
                }
                if (file.modifiedAt > 0) {
                    DetailRow(label = "Last Modified", value = formatDate(file.modifiedAt))
                }
                if (!file.folderPath.isNullOrBlank()) {
                    DetailRow(label = "Cloud Path", value = file.folderPath)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.isMedia) {
                    TextButton(onClick = { onOpenMedia(file) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View")
                    }
                }
                Button(onClick = { onDownload(file) }) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = java.util.Date(if (timestamp < 1000000000000L) timestamp * 1000 else timestamp)
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(date)
}

