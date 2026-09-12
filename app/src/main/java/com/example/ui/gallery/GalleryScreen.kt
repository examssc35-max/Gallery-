package com.example.ui.gallery

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeletionResult
import com.example.data.local.SortOrder
import com.example.domain.model.MediaItem
import com.example.domain.repository.R2Repository
import com.example.ui.albums.AlbumsScreen
import com.example.ui.cloud.CloudTabScreen
import com.example.ui.cloud.CloudTabViewModel
import com.example.ui.gallery.components.MediaGridItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    r2Repository: R2Repository,
    cloudTabViewModel: CloudTabViewModel,
    onOpenViewer: (initialIndex: Int, items: List<MediaItem>) -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDuplicates: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showColumnsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Check media permission
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        hasPermission = granted
        if (granted) {
            viewModel.loadMedia()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    // Android 11+ MediaStore Delete IntentSender launcher
    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeletionConfirmed()
            scope.launch { snackbarHostState.showSnackbar("Deleted successfully") }
        }
    }

    // React to deletion result from ViewModel
    LaunchedEffect(uiState.deletionResult) {
        uiState.deletionResult?.let { res ->
            when (res) {
                is DeletionResult.RequiresUserConsent -> {
                    val req = IntentSenderRequest.Builder(res.intentSender).build()
                    deleteIntentSenderLauncher.launch(req)
                }
                is DeletionResult.Success -> {
                    snackbarHostState.showSnackbar("Deleted successfully")
                    viewModel.clearDeletionResult()
                }
                is DeletionResult.Failure -> {
                    snackbarHostState.showSnackbar("Deletion failed: ${res.error}")
                    viewModel.clearDeletionResult()
                }
            }
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    val filteredItems = remember(uiState, sortOrder) {
        viewModel.getFilteredItems(sortOrder)
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Multi-selection Top Bar
                TopAppBar(
                    title = {
                        Text("${uiState.selectedIds.size} Selected", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectAll(filteredItems) },
                            modifier = Modifier.testTag("select_all_btn")
                        ) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                // Standard Top Bar
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search photos & videos...") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.setSearchQuery("")
                                        isSearchActive = false
                                    }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close search")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gallery_search_input")
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (uiState.activeAlbumName != null) uiState.activeAlbumName!! else "CloudGallery",
                                    fontWeight = FontWeight.Bold
                                )
                                if (uiState.activeAlbumName != null) {
                                    IconButton(onClick = { viewModel.selectTab(GalleryTab.ALBUMS) }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Back to albums")
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                            }

                            // Columns Menu
                            Box {
                                IconButton(
                                    onClick = { showColumnsMenu = true },
                                    modifier = Modifier.testTag("columns_button")
                                ) {
                                    Icon(imageVector = Icons.Default.GridView, contentDescription = "Grid Size")
                                }
                                DropdownMenu(
                                    expanded = showColumnsMenu,
                                    onDismissRequest = { showColumnsMenu = false }
                                ) {
                                    listOf(2, 3, 4, 5).forEach { cols ->
                                        DropdownMenuItem(
                                            text = { Text("$cols Columns ${if (cols == gridColumns) "✓" else ""}") },
                                            onClick = {
                                                viewModel.setGridColumns(cols)
                                                showColumnsMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Sort Menu
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("sort_button")
                                ) {
                                    Icon(imageVector = Icons.Default.FilterList, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Date (Newest First) ${if (sortOrder == SortOrder.DATE_DESC) "✓" else ""}") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.DATE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date (Oldest First) ${if (sortOrder == SortOrder.DATE_ASC) "✓" else ""}") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.DATE_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (A to Z) ${if (sortOrder == SortOrder.NAME_ASC) "✓" else ""}") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.NAME_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Size (Largest First) ${if (sortOrder == SortOrder.SIZE_DESC) "✓" else ""}") },
                                        onClick = {
                                            viewModel.setSortOrder(SortOrder.SIZE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }

                            // Overflow Menu
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("overflow_menu_button")
                                ) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                        text = { Text("Automatic Backup") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToBackup()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                                        text = { Text("R2 Cloud Browser") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToCloud()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        text = { Text("Duplicate Finder") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToDuplicates()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                        text = { Text("Storage Analyzer") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToStorage()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        text = { Text("Recycle Bin") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToTrash()
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        text = { Text("Settings & R2") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Primary Scrollable Tab Row: Horizontally scrollable so tabs fit comfortably without crowding
                PrimaryScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GalleryTab.values().forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tab == GalleryTab.CLOUD) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = if (uiState.selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = when (tab) {
                                            GalleryTab.ALL -> "All"
                                            GalleryTab.PHOTOS -> "Photos"
                                            GalleryTab.VIDEOS -> "Videos"
                                            GalleryTab.FAVORITES -> "Favorites"
                                            GalleryTab.ALBUMS -> "Albums"
                                            GalleryTab.CLOUD -> "Cloud"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Batch upload progress indicator
                if (uiState.isBatchUploading && uiState.batchUploadProgress != null) {
                    val (current, total) = uiState.batchUploadProgress!!
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                text = "Backing up to R2: $current / $total items",
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

                // Tab Content
                when {
                    uiState.selectedTab == GalleryTab.CLOUD -> {
                        CloudTabScreen(
                            viewModel = cloudTabViewModel,
                            r2Repository = r2Repository,
                            onOpenViewer = onOpenViewer,
                            onConnectR2 = onNavigateToSettings,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    uiState.selectedTab == GalleryTab.ALBUMS && uiState.activeAlbumName == null -> {
                        AlbumsScreen(
                            albums = uiState.albums,
                            onAlbumClick = { albumName -> viewModel.selectAlbum(albumName) }
                        )
                    }
                    else -> {
                        // Photos / Videos Grid
                        if (uiState.isLoading && uiState.mediaItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (filteredItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (uiState.searchQuery.isNotEmpty())
                                            "No items match '${uiState.searchQuery}'"
                                        else
                                            "No media items in this section",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                                    MediaGridItem(
                                        item = item,
                                        isSelected = item.id in uiState.selectedIds,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isBackedUp = item.id in uiState.backedUpIds,
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
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar when multi-selection is active
            AnimatedVisibility(
                visible = uiState.isSelectionMode && uiState.selectedIds.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selectedItems = filteredItems.filter { it.id in uiState.selectedIds }

                        // 1. Save to R2
                        TextButton(
                            onClick = { viewModel.backupSelected(selectedItems) },
                            modifier = Modifier.testTag("batch_save_r2_btn")
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to R2")
                        }

                        // 2. Share
                        TextButton(
                            onClick = {
                                shareMultipleMedia(context, selectedItems)
                            },
                            modifier = Modifier.testTag("batch_share_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        // 3. Delete
                        TextButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("batch_delete_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val selectedItems = filteredItems.filter { it.id in uiState.selectedIds }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedItems.size} items?") },
            text = {
                Text("This will permanently remove the selected ${selectedItems.size} items from your device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelected(selectedItems)
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

private fun shareMultipleMedia(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    try {
        val uris = ArrayList<Uri>(items.map { Uri.parse(it.uriString) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${items.size} items"))
    } catch (_: Exception) {}
}
