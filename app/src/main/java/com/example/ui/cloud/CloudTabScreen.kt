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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SortOrder
import com.example.domain.model.MediaItem
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
                onShowSortMenu = { showSortMenu = true }
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
                            text = "Downloading from R2: $current / $total items",
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
                    CloudNotConnectedState(onConnectClick = onConnectR2)
                }

                // 2. Loading initial data
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading Cloudflare R2 files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Connected but bucket is empty (No items and no folders)
                filteredItems.isEmpty() && uiState.folders.isEmpty() -> {
                    CloudEmptyBucketState(
                        searchQuery = uiState.searchQuery,
                        currentPrefix = uiState.currentPrefix,
                        onRefresh = { viewModel.refresh() }
                    )
                }

                // 4. Populated bucket: Gallery Grid
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

                            // Media Items
                            itemsIndexed(
                                items = filteredItems,
                                key = { _, item -> item.cloudKey ?: item.path }
                            ) { index, item ->
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
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                        Text(
                            text = "${uiState.selectedIds.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        // Select All
                        IconButton(
                            onClick = { viewModel.selectAll(filteredItems) },
                            modifier = Modifier.testTag("cloud_select_all_button")
                        ) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                        }

                        // Batch Download
                        IconButton(
                            onClick = {
                                val selectedItems = filteredItems.filter { it.id in uiState.selectedIds }
                                isBatchDownloading = true
                                batchDownloadProgress = Pair(0, selectedItems.size)
                                scope.launch {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    var count = 0
                                    for ((idx, item) in selectedItems.withIndex()) {
                                        batchDownloadProgress = Pair(idx + 1, selectedItems.size)
                                        val key = item.cloudKey ?: item.path
                                        val targetFile = File(downloadsDir, item.name)
                                        val res = r2Repository.downloadKeyToFile(key, targetFile)
                                        if (res.isSuccess) {
                                            count++
                                            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                                        }
                                    }
                                    isBatchDownloading = false
                                    batchDownloadProgress = null
                                    viewModel.clearSelection()
                                    snackbarHostState.showSnackbar("Downloaded $count items to Downloads")
                                }
                            },
                            modifier = Modifier.testTag("cloud_download_selected_button")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Download Selected")
                        }

                        // Batch Delete
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("cloud_delete_selected_button")
                        ) {
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
            title = { Text("Delete from Cloudflare R2") },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedItems.size} file(s) from your Cloudflare R2 bucket? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelected(selectedItems) { deletedCount ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Deleted $deletedCount files from Cloudflare R2")
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
    onShowSortMenu: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                onSearchQueryChange("")
                                onSearchActiveChange(false)
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_search_input")
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
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(folders) { folderKey ->
            val folderName = folderKey.removePrefix(currentPrefix).trimEnd('/')
            if (folderName.isNotEmpty()) {
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
                text = "Cloud storage isn't connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect your Cloudflare R2 bucket to view and manage your uploaded photos and videos directly in CloudGallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConnectClick,
                modifier = Modifier.testTag("connect_cloudflare_r2_button")
            ) {
                Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Cloudflare R2")
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
                    "This bucket is empty."
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
                    "Files you save or backup to Cloudflare R2 will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Bucket")
            }
        }
    }
}
