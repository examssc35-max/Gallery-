package com.example.ui.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.SortOrder
import com.example.domain.model.CloudFileType
import com.example.domain.model.CloudFileTypeResolver
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudTabScreen(
    viewModel: CloudTabViewModel,
    r2Repository: R2Repository,
    onOpenViewer: (initialIndex: Int, items: List<MediaItem>) -> Unit,
    onConnectR2: () -> Unit,
    onNavigateToStorageUsage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<R2Item?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // File picker launcher for uploading any file to R2
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadFileFromUri(context, uri) { success, err ->
                if (success) {
                    Toast.makeText(context, "File uploaded to Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, err ?: "Upload failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val filteredFiles = remember(uiState) { viewModel.getFilteredFiles() }
    val filteredMediaItems = remember(uiState) { viewModel.getFilteredMediaItems() }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top action & search header
            CloudTabHeader(
                searchQuery = uiState.searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                isGridView = uiState.isGridView,
                onToggleView = { viewModel.toggleViewMode() },
                onShowSortMenu = { showSortMenu = true },
                onShowOptionsMenu = { showOptionsMenu = true },
                onUploadClick = { filePickerLauncher.launch("*/*") },
                onCreateFolderClick = {
                    newFolderName = ""
                    showCreateFolderDialog = true
                }
            )

            // File type filter chips row
            FileTypeFilterChips(
                activeFilter = uiState.fileTypeFilter,
                onSelectFilter = { viewModel.setFileTypeFilter(it) }
            )

            // Breadcrumbs Navigation Bar
            BreadcrumbBar(
                breadcrumbs = viewModel.getBreadcrumbs(),
                onBreadcrumbClick = { prefix -> viewModel.navigateToFolder(prefix) },
                onNavigateUp = { viewModel.navigateUp() },
                canNavigateUp = uiState.currentPrefix.isNotEmpty()
            )

            // Selection Mode Bar
            AnimatedVisibility(visible = uiState.isSelectionMode) {
                SelectionActionBar(
                    selectedCount = uiState.selectedKeys.size,
                    onSelectAll = { viewModel.selectAll(filteredFiles) },
                    onClearSelection = { viewModel.clearSelection() },
                    onDownloadSelected = {
                        viewModel.downloadSelected(context) { count ->
                            Toast.makeText(context, "Downloaded $count files to Downloads", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteSelected = { showBatchDeleteConfirm = true }
                )
            }

            // Uploading progress banner
            if (uiState.isUploading) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Uploading to Cloudflare R2...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.uploadProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { uiState.uploadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Batch downloading progress banner
            if (uiState.isBatchDownloading && uiState.batchDownloadProgress != null) {
                val (curr, total) = uiState.batchDownloadProgress!!
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Downloading $curr of $total files...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Main Content Area
            when {
                // 1. Not connected state
                !uiState.isConnected -> {
                    CloudNotConnectedView(
                        onConfigureClick = onConnectR2
                    )
                }

                // 2. Loading initial data
                uiState.isLoading && uiState.r2Files.isEmpty() && uiState.folders.isEmpty() && uiState.errorMessage == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading files from Cloudflare R2...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Error state when no files/folders could be loaded
                uiState.errorMessage != null && uiState.r2Files.isEmpty() && uiState.folders.isEmpty() -> {
                    CloudErrorView(
                        message = uiState.errorMessage ?: "Failed to load files from Cloudflare R2",
                        onRetry = { viewModel.refresh() }
                    )
                }

                // 4. Connected but filtered view or bucket/folder is empty
                !uiState.isLoading && filteredFiles.isEmpty() -> {
                    CloudEmptyView(
                        searchQuery = uiState.searchQuery,
                        currentPrefix = uiState.currentPrefix,
                        activeFilter = uiState.fileTypeFilter,
                        onClearFilter = { viewModel.setFileTypeFilter(null) },
                        onClearSearch = { viewModel.setSearchQuery("") },
                        onRefresh = { viewModel.refresh() },
                        onUploadClick = { filePickerLauncher.launch("*/*") },
                        onCreateFolderClick = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        }
                    )
                }

                // 5. File Browser (Grid or List)
                else -> {
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
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredFiles,
                                    key = { it.key }
                                ) { item ->
                                    CloudGridItem(
                                        item = item,
                                        isSelected = item.key in uiState.selectedKeys,
                                        isSelectionMode = uiState.isSelectionMode,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                if (!item.isFolder) viewModel.toggleSelection(item.key)
                                            } else if (item.isFolder) {
                                                viewModel.navigateToFolder(item.key)
                                            } else if (item.isMedia) {
                                                val mediaItem = item.toMediaItem()
                                                val mediaIdx = filteredMediaItems.indexOfFirst {
                                                    it.cloudKey == item.key || it.path == item.key
                                                }
                                                if (mediaIdx >= 0) {
                                                    onOpenViewer(mediaIdx, filteredMediaItems)
                                                } else {
                                                    onOpenViewer(0, listOf(mediaItem))
                                                }
                                            } else {
                                                viewModel.openItemDetails(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (!item.isFolder) {
                                                viewModel.toggleSelection(item.key)
                                            }
                                        },
                                        onDetailsClick = { viewModel.openItemDetails(item) }
                                    )
                                }

                                if (uiState.hasMore) {
                                    item(span = { GridItemSpan(gridColumns) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.isLoadingMore) {
                                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                            } else {
                                                FilledTonalButton(onClick = { viewModel.loadMore() }) {
                                                    Text("Load More Files")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(vertical = 4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredFiles,
                                    key = { it.key }
                                ) { item ->
                                    CloudListItem(
                                        item = item,
                                        isSelected = item.key in uiState.selectedKeys,
                                        isSelectionMode = uiState.isSelectionMode,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                if (!item.isFolder) viewModel.toggleSelection(item.key)
                                            } else if (item.isFolder) {
                                                viewModel.navigateToFolder(item.key)
                                            } else if (item.isMedia) {
                                                val mediaItem = item.toMediaItem()
                                                val mediaIdx = filteredMediaItems.indexOfFirst {
                                                    it.cloudKey == item.key || it.path == item.key
                                                }
                                                if (mediaIdx >= 0) {
                                                    onOpenViewer(mediaIdx, filteredMediaItems)
                                                } else {
                                                    onOpenViewer(0, listOf(mediaItem))
                                                }
                                            } else {
                                                viewModel.openItemDetails(item)
                                            }
                                        },
                                        onLongClick = {
                                            if (!item.isFolder) {
                                                viewModel.toggleSelection(item.key)
                                            }
                                        },
                                        onDownloadClick = {
                                            viewModel.downloadItem(context, item) { ok, path ->
                                                if (ok) {
                                                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onDeleteClick = { itemToDelete = item },
                                        onDetailsClick = { viewModel.openItemDetails(item) }
                                    )
                                }

                                if (uiState.hasMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.isLoadingMore) {
                                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                            } else {
                                                FilledTonalButton(onClick = { viewModel.loadMore() }) {
                                                    Text("Load More Files")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort Menu Dropdown
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Date (Newest First) ${if (uiState.sortOrder == SortOrder.DATE_DESC) "✓" else ""}") },
                onClick = {
                    viewModel.setSortOrder(SortOrder.DATE_DESC)
                    showSortMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Date (Oldest First) ${if (uiState.sortOrder == SortOrder.DATE_ASC) "✓" else ""}") },
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

        // Options Menu Dropdown
        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
        ) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                text = { Text("Refresh R2") },
                onClick = {
                    showOptionsMenu = false
                    viewModel.refresh()
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                text = { Text("R2 Storage Usage") },
                onClick = {
                    showOptionsMenu = false
                    onNavigateToStorageUsage()
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                text = { Text("Upload File") },
                onClick = {
                    showOptionsMenu = false
                    filePickerLauncher.launch("*/*")
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                text = { Text("New Folder") },
                onClick = {
                    showOptionsMenu = false
                    newFolderName = ""
                    showCreateFolderDialog = true
                }
            )
        }

        // Create Folder Dialog
        if (showCreateFolderDialog) {
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text("Create Folder") },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                viewModel.createFolder(newFolderName) { ok, err ->
                                    if (ok) {
                                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                                        showCreateFolderDialog = false
                                    } else {
                                        Toast.makeText(context, err ?: "Failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Single Item Delete Confirmation Dialog
        if (itemToDelete != null) {
            val item = itemToDelete!!
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete from R2") },
                text = { Text("Are you sure you want to permanently delete '${item.name}' from your Cloudflare R2 bucket?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSingleItem(item) { ok ->
                                if (ok) Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                            }
                            itemToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Batch Delete Confirmation Dialog
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text("Delete Selected Files") },
                text = { Text("Are you sure you want to permanently delete ${uiState.selectedKeys.size} selected file(s) from your Cloudflare R2 bucket?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSelected { count ->
                                Toast.makeText(context, "Deleted $count files", Toast.LENGTH_SHORT).show()
                            }
                            showBatchDeleteConfirm = false
                        }
                    ) {
                        Text("Delete All", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // File Details Modal
        uiState.selectedItemForDetails?.let { file ->
            R2FileDetailsDialog(
                file = file,
                onDismiss = { viewModel.closeItemDetails() },
                onDownload = {
                    viewModel.downloadItem(context, file) { ok, path ->
                        if (ok) {
                            Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = {
                    itemToDelete = file
                    viewModel.closeItemDetails()
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CloudTabHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onShowSortMenu: () -> Unit,
    onShowOptionsMenu: () -> Unit,
    onUploadClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search files & folders...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("cloud_search_field")
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Upload Button
                IconButton(onClick = onUploadClick, modifier = Modifier.testTag("cloud_upload_button")) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = "Upload to R2")
                }

                // New Folder Button
                IconButton(onClick = onCreateFolderClick, modifier = Modifier.testTag("cloud_new_folder_button")) {
                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                }

                // Grid / List Toggle
                IconButton(onClick = onToggleView, modifier = Modifier.testTag("cloud_view_toggle_button")) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "List View" else "Grid View"
                    )
                }

                // Sort & More
                IconButton(onClick = onShowSortMenu, modifier = Modifier.testTag("cloud_sort_button")) {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = "Sort")
                }

                IconButton(onClick = onShowOptionsMenu, modifier = Modifier.testTag("cloud_options_button")) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                }
            }
        }
    }
}

@Composable
private fun FileTypeFilterChips(
    activeFilter: CloudFileType?,
    onSelectFilter: (CloudFileType?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = activeFilter == null,
            onClick = { onSelectFilter(null) },
            label = { Text("All", fontSize = 12.sp) },
            modifier = Modifier.testTag("chip_all")
        )

        CloudFileType.values().forEach { type ->
            if (type != CloudFileType.FOLDER) {
                FilterChip(
                    selected = activeFilter == type,
                    onClick = {
                        onSelectFilter(if (activeFilter == type) null else type)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = CloudFileTypeResolver.getIcon(type),
                            contentDescription = null,
                            tint = CloudFileTypeResolver.getIconColor(type),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text(type.displayName, fontSize = 12.sp) },
                    modifier = Modifier.testTag("chip_${type.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<Pair<String, String>>,
    onBreadcrumbClick: (String) -> Unit,
    onNavigateUp: () -> Unit,
    canNavigateUp: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canNavigateUp) {
                IconButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Up folder",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { idx, (label, prefix) ->
                    val isLast = idx == breadcrumbs.lastIndex
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(!isLast) { onBreadcrumbClick(prefix) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    if (!isLast) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll) {
                    Text("Select All", fontSize = 12.sp)
                }
                IconButton(onClick = onDownloadSelected, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download selected")
                }
                IconButton(onClick = onDeleteSelected, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudGridItem(
    item: R2Item,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val context = LocalContext.current

    if (item.isFolder) {
        // Folder card in grid
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.name.removeSuffix("/"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else if (item.isMedia && !item.downloadUrl.isNullOrEmpty()) {
        // Photo or Video item with thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.downloadUrl)
                    .crossfade(true)
                    .memoryCacheKey("r2_thumb_${item.key}")
                    .diskCacheKey("r2_thumb_${item.key}")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.fileType == CloudFileType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = item.name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )

            // Video play icon indicator
            if (item.fileType == CloudFileType.VIDEO) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Size pill at bottom
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Selection indicator
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    } else {
        // Non-media file (Document, Archive, APK, Audio, Text, Other)
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = CloudFileTypeResolver.getIcon(item.fileType),
                        contentDescription = item.fileType.displayName,
                        tint = CloudFileTypeResolver.getIconColor(item.fileType),
                        modifier = Modifier.size(36.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(item.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudListItem(
    item: R2Item,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode && !item.isFolder) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
            )
        }

        // File icon or thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CloudFileTypeResolver.getIconColor(item.fileType).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isMedia && !item.downloadUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.downloadUrl)
                        .crossfade(true)
                        .memoryCacheKey("r2_thumb_${item.key}")
                        .diskCacheKey("r2_thumb_${item.key}")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    },
                    error = {
                        Icon(
                            imageVector = CloudFileTypeResolver.getIcon(item.fileType),
                            contentDescription = null,
                            tint = CloudFileTypeResolver.getIconColor(item.fileType),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            } else {
                Icon(
                    imageVector = CloudFileTypeResolver.getIcon(item.fileType),
                    contentDescription = null,
                    tint = CloudFileTypeResolver.getIconColor(item.fileType),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // File details (Name, size, date)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (item.isFolder) item.name.removeSuffix("/") else item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isFolder) {
                    Text(
                        text = formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (item.lastModified > 0) formatDate(item.lastModified) else "Cloudflare R2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!item.isFolder) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        text = { Text("Details") },
                        onClick = {
                            showMenu = false
                            onDetailsClick()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownloadClick()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun R2FileDetailsDialog(
    file: R2Item,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CloudFileTypeResolver.getIcon(file.fileType),
                    contentDescription = null,
                    tint = CloudFileTypeResolver.getIconColor(file.fileType),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DetailRow("Type", file.fileType.displayName)
                DetailRow("Size", "${formatFileSize(file.size)} (${file.size} bytes)")
                DetailRow("R2 Key", file.key)
                if (file.mimeType.isNotEmpty()) {
                    DetailRow("MIME Type", file.mimeType)
                }
                if (file.lastModified > 0) {
                    DetailRow("Last Modified", formatDate(file.lastModified))
                }

                if (!file.downloadUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(file.downloadUrl))
                            Toast.makeText(context, "Presigned URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Presigned Link")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onDismiss()
                    onDownload()
                }) {
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
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CloudNotConnectedView(
    onConfigureClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloudflare R2",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cloudflare R2 Storage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect your Cloudflare R2 bucket to browse files, auto-backup photos, and enjoy zero-egress fee cloud storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onConfigureClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("configure_r2_button")
                ) {
                    Text("Configure Cloudflare R2")
                }
            }
        }
    }
}

@Composable
private fun CloudErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Failed to load files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun CloudEmptyView(
    searchQuery: String,
    currentPrefix: String,
    activeFilter: CloudFileType? = null,
    onClearFilter: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onUploadClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Icon(
                imageVector = if (activeFilter != null) CloudFileTypeResolver.getIcon(activeFilter) else Icons.Default.Folder,
                contentDescription = null,
                tint = if (activeFilter != null) CloudFileTypeResolver.getIconColor(activeFilter).copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    searchQuery.isNotEmpty() -> "No files match '$searchQuery'"
                    activeFilter != null -> "No ${activeFilter.displayName} found"
                    else -> "This folder is empty"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    searchQuery.isNotEmpty() -> "Try searching with a different keyword"
                    activeFilter != null -> "No ${activeFilter.displayName.lowercase()} in ${if (currentPrefix.isEmpty()) "bucket root" else currentPrefix}"
                    currentPrefix.isEmpty() -> "Your Cloudflare R2 bucket has no files in root"
                    else -> "Path: $currentPrefix"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            when {
                searchQuery.isNotEmpty() -> {
                    Button(onClick = onClearSearch, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Search")
                    }
                }
                activeFilter != null -> {
                    Button(onClick = onClearFilter, shape = RoundedCornerShape(12.dp)) {
                        Text("Show All Files")
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onUploadClick) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload")
                        }
                        FilledTonalButton(onClick = onCreateFolderClick) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Folder")
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
