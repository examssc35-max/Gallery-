package com.example.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.DeletionResult
import com.example.data.local.SortOrder
import com.example.domain.model.MediaItem
import com.example.domain.repository.R2Repository
import com.example.ui.albums.AlbumsScreen
import com.example.ui.cloud.CloudTabScreen
import com.example.ui.cloud.CloudTabViewModel
import com.example.ui.common.FileOptionsBottomSheet
import com.example.ui.gallery.components.MediaGridItem
import com.example.ui.theme.DynamicAppBackground
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
    onNavigateToCloudStorageUsage: () -> Unit = {},
    onNavigateToAiAssistant: () -> Unit = {},
    smartCollectionsViewModel: com.example.ui.smartcollections.SmartCollectionsViewModel? = null,
    onNavigateToSmartCollections: () -> Unit = {},
    onCollectionClick: (com.example.domain.model.SmartCollection) -> Unit = {},
    onNavigateToSmartCollectionsSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val cloudTabUiState by cloudTabViewModel.uiState.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showColumnsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Sub-filter chips for Photos & Videos tabs
    var mediaSubFilter by remember { mutableStateOf("All") }

    // Bottom Sheet for single item actions
    var itemForOptionsSheet by remember { mutableStateOf<MediaItem?>(null) }

    // Navigation root check for back navigation
    val isAtRoot = !isSearchActive &&
        !uiState.isSelectionMode &&
        itemForOptionsSheet == null &&
        !(uiState.selectedTab == GalleryTab.ALBUMS && uiState.activeAlbumName != null) &&
        !(uiState.selectedTab == GalleryTab.CLOUD && (cloudTabUiState.isSelectionMode || cloudTabUiState.currentPrefix.isNotEmpty())) &&
        uiState.selectedTab == GalleryTab.ALL

    BackHandler(enabled = !isAtRoot) {
        when {
            itemForOptionsSheet != null -> {
                itemForOptionsSheet = null
            }
            isSearchActive -> {
                isSearchActive = false
                viewModel.setSearchQuery("")
            }
            uiState.isSelectionMode -> {
                viewModel.clearSelection()
            }
            uiState.selectedTab == GalleryTab.ALBUMS && uiState.activeAlbumName != null -> {
                viewModel.clearActiveAlbum()
            }
            uiState.selectedTab == GalleryTab.CLOUD -> {
                when {
                    cloudTabUiState.isSelectionMode -> cloudTabViewModel.clearSelection()
                    cloudTabUiState.currentPrefix.isNotEmpty() -> cloudTabViewModel.navigateUp()
                    else -> viewModel.selectTab(GalleryTab.ALL)
                }
            }
            uiState.selectedTab != GalleryTab.ALL -> {
                viewModel.selectTab(GalleryTab.ALL)
            }
        }
    }

    // Permission handling
    fun checkHasPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    var hasPermission by remember { mutableStateOf(checkHasPermission()) }

    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPerm = checkHasPermission()
                hasPermission = currentPerm
                if (currentPerm) {
                    viewModel.refreshMedia(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android 11+ Delete IntentSender launcher
    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeletionConfirmed()
            scope.launch { snackbarHostState.showSnackbar("Deleted successfully") }
        }
    }

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

    // Filtered items based on active tab and sub-filters
    val baseFilteredItems = remember(uiState, sortOrder) {
        viewModel.getFilteredItems(sortOrder)
    }

    val filteredItems = remember(baseFilteredItems, mediaSubFilter, uiState.selectedTab) {
        when (uiState.selectedTab) {
            GalleryTab.PHOTOS, GalleryTab.VIDEOS -> {
                when (mediaSubFilter) {
                    "Favorites" -> baseFilteredItems.filter { it.isFavorite }
                    "Camera" -> baseFilteredItems.filter { it.albumName.contains("Camera", ignoreCase = true) || it.path.contains("DCIM", ignoreCase = true) }
                    "Screenshots" -> baseFilteredItems.filter { it.albumName.contains("Screenshots", ignoreCase = true) || it.path.contains("Screenshots", ignoreCase = true) }
                    "Downloads" -> baseFilteredItems.filter { it.albumName.contains("Download", ignoreCase = true) || it.path.contains("Download", ignoreCase = true) }
                    else -> baseFilteredItems
                }
            }
            else -> baseFilteredItems
        }
    }

    val allPhotosCount = remember(uiState.mediaItems) { uiState.mediaItems.count { !it.isVideo } }
    val allVideosCount = remember(uiState.mediaItems) { uiState.mediaItems.count { it.isVideo } }
    val albumsCount = remember(uiState.albums) { uiState.albums.size }

    DynamicAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (uiState.isSelectionMode) {
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchActive) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search photos, videos, files...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
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
                                Column {
                                    val titleText = when (uiState.selectedTab) {
                                        GalleryTab.ALL -> if (uiState.activeAlbumName != null) uiState.activeAlbumName!! else "CloudGallery"
                                        GalleryTab.PHOTOS -> "Photos"
                                        GalleryTab.VIDEOS -> "Videos"
                                        GalleryTab.ALBUMS -> if (uiState.activeAlbumName != null) uiState.activeAlbumName!! else "Albums"
                                        GalleryTab.CLOUD -> "Cloud Storage"
                                        else -> "CloudGallery"
                                    }
                                    Text(
                                        text = titleText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                    if (uiState.selectedTab == GalleryTab.PHOTOS && uiState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "$allPhotosCount photos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (uiState.selectedTab == GalleryTab.VIDEOS && uiState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "$allVideosCount videos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (!isSearchActive) {
                                IconButton(
                                    onClick = onNavigateToAiAssistant,
                                    modifier = Modifier.testTag("ai_assistant_top_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Assistant",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.testTag("search_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                                }

                                if (uiState.selectedTab == GalleryTab.ALL || uiState.selectedTab == GalleryTab.PHOTOS || uiState.selectedTab == GalleryTab.VIDEOS) {
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
                                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                            text = { Text("Select Items") },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.toggleSelectionMode()
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
                                            leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                                            text = { Text("Cloud Storage Usage") },
                                            onClick = {
                                                showOverflowMenu = false
                                                onNavigateToCloudStorageUsage()
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            text = { Text("Settings") },
                                            onClick = {
                                                showOverflowMenu = false
                                                onNavigateToSettings()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                if (!uiState.isSelectionMode) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == GalleryTab.ALL,
                            onClick = {
                                if (uiState.selectedTab == GalleryTab.ALBUMS && uiState.activeAlbumName != null) {
                                    viewModel.clearActiveAlbum()
                                }
                                viewModel.selectTab(GalleryTab.ALL)
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == GalleryTab.PHOTOS,
                            onClick = {
                                mediaSubFilter = "All"
                                viewModel.selectTab(GalleryTab.PHOTOS)
                            },
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Photos") },
                            label = { Text("Photos", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == GalleryTab.VIDEOS,
                            onClick = {
                                mediaSubFilter = "All"
                                viewModel.selectTab(GalleryTab.VIDEOS)
                            },
                            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                            label = { Text("Videos", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == GalleryTab.ALBUMS,
                            onClick = { viewModel.selectTab(GalleryTab.ALBUMS) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Albums") },
                            label = { Text("Albums", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == GalleryTab.CLOUD,
                            onClick = { viewModel.selectTab(GalleryTab.CLOUD) },
                            icon = { Icon(Icons.Default.Cloud, contentDescription = "Cloud") },
                            label = { Text("Cloud", fontSize = 11.sp) }
                        )
                    }
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
                    // Filter Chips Bar for Photos and Videos tabs
                    if (uiState.selectedTab == GalleryTab.PHOTOS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Camera", "Screenshots", "Favorites").forEach { chipLabel ->
                                FilterChip(
                                    selected = mediaSubFilter == chipLabel,
                                    onClick = { mediaSubFilter = chipLabel },
                                    label = { Text(chipLabel, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    } else if (uiState.selectedTab == GalleryTab.VIDEOS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Camera", "Downloads", "Favorites").forEach { chipLabel ->
                                FilterChip(
                                    selected = mediaSubFilter == chipLabel,
                                    onClick = { mediaSubFilter = chipLabel },
                                    label = { Text(chipLabel, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
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

                    val isRefreshing = when (uiState.selectedTab) {
                        GalleryTab.CLOUD -> cloudTabUiState.isRefreshing
                        else -> uiState.isRefreshing
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            if (uiState.selectedTab == GalleryTab.CLOUD) {
                                cloudTabViewModel.refresh()
                            } else {
                                viewModel.refreshMedia(silent = false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (uiState.selectedTab) {
                            GalleryTab.CLOUD -> {
                                CloudTabScreen(
                                    viewModel = cloudTabViewModel,
                                    r2Repository = r2Repository,
                                    onOpenViewer = onOpenViewer,
                                    onConnectR2 = onNavigateToSettings,
                                    onNavigateToStorageUsage = onNavigateToCloudStorageUsage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            GalleryTab.ALBUMS -> {
                                if (uiState.activeAlbumName == null) {
                                    AlbumsScreen(
                                        albums = uiState.albums,
                                        onAlbumClick = { albumName -> viewModel.selectAlbum(albumName) }
                                    )
                                } else {
                                    // Render active album items
                                    MediaGridView(
                                        items = filteredItems,
                                        gridColumns = gridColumns,
                                        selectedIds = uiState.selectedIds,
                                        isSelectionMode = uiState.isSelectionMode,
                                        backedUpIds = uiState.backedUpIds,
                                        onItemClick = { index, item ->
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                onOpenViewer(index, filteredItems)
                                            }
                                        },
                                        onItemLongClick = { item ->
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                itemForOptionsSheet = item
                                            }
                                        }
                                    )
                                }
                            }
                            GalleryTab.ALL -> {
                                // Panel 1: Compact, Content-Focused Home Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    // Compact Category Cards Row (Photos, Videos, Albums, Cloud)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HomeCategoryCard(
                                            title = "Photos",
                                            count = allPhotosCount.toString(),
                                            icon = Icons.Default.PhotoLibrary,
                                            iconColor = Color(0xFF1976D2),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                mediaSubFilter = "All"
                                                viewModel.selectTab(GalleryTab.PHOTOS)
                                            }
                                        )
                                        HomeCategoryCard(
                                            title = "Videos",
                                            count = allVideosCount.toString(),
                                            icon = Icons.Default.VideoLibrary,
                                            iconColor = Color(0xFF9C27B0),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                mediaSubFilter = "All"
                                                viewModel.selectTab(GalleryTab.VIDEOS)
                                            }
                                        )
                                        HomeCategoryCard(
                                            title = "Albums",
                                            count = albumsCount.toString(),
                                            icon = Icons.Default.Folder,
                                            iconColor = Color(0xFFFB8C00),
                                            modifier = Modifier.weight(1f),
                                            onClick = { viewModel.selectTab(GalleryTab.ALBUMS) }
                                        )
                                        HomeCategoryCard(
                                            title = "Cloud",
                                            count = "R2",
                                            icon = Icons.Default.Cloud,
                                            iconColor = Color(0xFF00897B),
                                            modifier = Modifier.weight(1f),
                                            onClick = { viewModel.selectTab(GalleryTab.CLOUD) }
                                        )
                                    }

                                    // Recent Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Recent",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(
                                            onClick = {
                                                mediaSubFilter = "All"
                                                viewModel.selectTab(GalleryTab.PHOTOS)
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "See All",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Content-focused Compact Grid
                                    MediaGridView(
                                        items = filteredItems,
                                        gridColumns = gridColumns,
                                        selectedIds = uiState.selectedIds,
                                        isSelectionMode = uiState.isSelectionMode,
                                        backedUpIds = uiState.backedUpIds,
                                        onItemClick = { index, item ->
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                onOpenViewer(index, filteredItems)
                                            }
                                        },
                                        onItemLongClick = { item ->
                                            if (uiState.isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                itemForOptionsSheet = item
                                            }
                                        }
                                    )
                                }
                            }
                            else -> {
                                // Photos or Videos Grid
                                MediaGridView(
                                    items = filteredItems,
                                    gridColumns = gridColumns,
                                    selectedIds = uiState.selectedIds,
                                    isSelectionMode = uiState.isSelectionMode,
                                    backedUpIds = uiState.backedUpIds,
                                    onItemClick = { index, item ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(item.id)
                                        } else {
                                            onOpenViewer(index, filteredItems)
                                        }
                                    },
                                    onItemLongClick = { item ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(item.id)
                                        } else {
                                            itemForOptionsSheet = item
                                        }
                                    }
                                )
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

                            TextButton(
                                onClick = { viewModel.backupSelected(selectedItems) },
                                modifier = Modifier.testTag("batch_save_r2_btn")
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to R2")
                            }

                            TextButton(
                                onClick = { shareMultipleMedia(context, selectedItems) },
                                modifier = Modifier.testTag("batch_share_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }

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
    }

    // Modern File Options Bottom Sheet (Panel 8)
    itemForOptionsSheet?.let { item ->
        FileOptionsBottomSheet(
            item = item,
            onDismiss = { itemForOptionsSheet = null },
            onOpen = {
                val idx = filteredItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                onOpenViewer(idx, filteredItems)
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(item.id)
            },
            onDownloadOrUpload = {
                viewModel.backupSelected(listOf(item))
            },
            onDelete = {
                viewModel.deleteSelected(listOf(item))
            }
        )
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

@Composable
private fun HomeCategoryCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediaGridView(
    items: List<MediaItem>,
    gridColumns: Int,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    backedUpIds: Set<Long>,
    onItemClick: (Int, MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No items in this section",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = modifier.fillMaxSize()
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                MediaGridItem(
                    item = item,
                    isSelected = item.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    isBackedUp = item.id in backedUpIds,
                    onClick = { onItemClick(index, item) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
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
