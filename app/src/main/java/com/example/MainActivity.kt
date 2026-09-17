package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.cloudflare.R2Client
import com.example.data.local.AppDatabase
import com.example.data.local.AppThemeMode
import com.example.data.local.MediaStoreDataSource
import com.example.data.local.PreferencesManager
import com.example.data.repository.BackupRepositoryImpl
import com.example.data.repository.MediaRepositoryImpl
import com.example.data.repository.R2RepositoryImpl
import com.example.domain.model.MediaItem
import com.example.domain.usecase.GetStorageUsageUseCase
import com.example.navigation.NavRoute
import com.example.ui.backup.BackupScreen
import com.example.ui.backup.BackupViewModel
import com.example.ui.cloud.CloudBrowserScreen
import com.example.ui.cloud.CloudBrowserViewModel
import com.example.ui.cloud.CloudTabViewModel
import com.example.ui.duplicate.DuplicateFinderScreen
import com.example.ui.gallery.GalleryScreen
import com.example.ui.gallery.GalleryViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.storage.CloudStorageUsageScreen
import com.example.ui.storage.StorageAnalyzerScreen
import com.example.ui.storage.StorageUsageViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.trash.TrashScreen
import com.example.ui.viewer.MediaViewerScreen
import com.example.ui.viewer.MediaViewerStateHolder

class MainActivity : ComponentActivity() {

    private var connectedServicesVm: com.example.ui.settings.ConnectedServicesViewModel? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthDeepLink(intent)
    }

    private fun handleOAuthDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "cloudgallery" && uri.host == "oauth") {
            connectedServicesVm?.handleAuthCallback(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val preferencesManager = PreferencesManager(applicationContext)
        val r2Client = R2Client()
        val r2Repository = R2RepositoryImpl(applicationContext, r2Client, preferencesManager)
        val mediaDataSource = MediaStoreDataSource(applicationContext)
        val mediaRepository = MediaRepositoryImpl(mediaDataSource, database)
        val backupRepository = BackupRepositoryImpl(
            context = applicationContext,
            mediaRepository = mediaRepository,
            r2Repository = r2Repository,
            database = database,
            preferencesManager = preferencesManager
        )

        val galleryViewModel = GalleryViewModel(mediaRepository, backupRepository, preferencesManager)
        val backupViewModel = BackupViewModel(backupRepository, r2Repository)
        val cloudViewModel = CloudBrowserViewModel(r2Repository)

        val secureTokenStorage = com.example.security.SecureCloudTokenStorage(applicationContext)
        val multiCloudRepository = com.example.data.repository.MultiCloudRepositoryImpl(
            r2Repository = r2Repository,
            tokenStorage = secureTokenStorage
        )
        val connectedServicesViewModel = com.example.ui.settings.ConnectedServicesViewModel(
            multiCloudRepository = multiCloudRepository,
            r2Repository = r2Repository,
            preferencesManager = preferencesManager,
            tokenStorage = secureTokenStorage
        )
        connectedServicesVm = connectedServicesViewModel
        handleOAuthDeepLink(intent)

        val cloudTabViewModel = CloudTabViewModel(
            r2Repository = r2Repository,
            backupRepository = backupRepository,
            preferencesManager = preferencesManager,
            multiCloudRepository = multiCloudRepository
        )
        val getStorageUsageUseCase = GetStorageUsageUseCase(r2Repository)
        val storageUsageViewModel = StorageUsageViewModel(getStorageUsageUseCase, r2Repository)

        val smartCollectionClassifier = com.example.ai.classifier.OnDeviceVisionClassifier(
            context = applicationContext,
            preferencesManager = preferencesManager
        )
        val smartCollectionRepository = com.example.data.repository.SmartCollectionRepositoryImpl(
            context = applicationContext,
            database = database,
            mediaRepository = mediaRepository,
            classifier = smartCollectionClassifier,
            preferencesManager = preferencesManager
        )
        val smartCollectionsViewModel = com.example.ui.smartcollections.SmartCollectionsViewModel(
            smartCollectionRepository = smartCollectionRepository,
            preferencesManager = preferencesManager
        )

        val galleryAssistantTools = com.example.ai.tools.GalleryAssistantToolsImpl(
            mediaRepository = mediaRepository,
            backupRepository = backupRepository,
            r2Repository = r2Repository,
            preferencesManager = preferencesManager,
            context = applicationContext,
            smartCollectionRepository = smartCollectionRepository
        )
        val aiAgentOrchestrator = com.example.ai.orchestrator.AiAgentOrchestrator(
            tools = galleryAssistantTools,
            preferencesManager = preferencesManager
        )
        val aiAssistantViewModel = com.example.ui.ai.AiAssistantViewModel(
            orchestrator = aiAgentOrchestrator,
            preferencesManager = preferencesManager
        )

        setContent {
            val themeMode by preferencesManager.themeModeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    // Shared state for media viewer list
                    var activeViewerList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    var activeViewerIndex by remember { mutableIntStateOf(0) }
                    var activeViewerAutoPlayVideo by remember { mutableStateOf(false) }

                    NavHost(
                        navController = navController,
                        startDestination = NavRoute.Gallery.route
                    ) {
                        composable(NavRoute.Gallery.route) {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                r2Repository = r2Repository,
                                cloudTabViewModel = cloudTabViewModel,
                                smartCollectionsViewModel = smartCollectionsViewModel,
                                onCollectionClick = { collection ->
                                    navController.navigate(NavRoute.SmartCollectionDetail.createRoute(collection.id))
                                },
                                onNavigateToSmartCollections = {
                                    navController.navigate(NavRoute.SmartCollections.route)
                                },
                                onNavigateToSmartCollectionsSettings = {
                                    navController.navigate(NavRoute.SmartCollectionsSettings.route)
                                },
                                onOpenViewer = { index, items ->
                                    com.example.ui.viewer.MediaViewerStateHolder.setViewerData(items, index, false)
                                    activeViewerList = items
                                    activeViewerIndex = index
                                    activeViewerAutoPlayVideo = false
                                    navController.navigate(NavRoute.MediaViewer.createRoute(index))
                                },
                                onNavigateToBackup = { navController.navigate(NavRoute.Backup.route) },
                                onNavigateToCloud = { navController.navigate(NavRoute.Cloud.route) },
                                onNavigateToSettings = { navController.navigate(NavRoute.Settings.route) },
                                onNavigateToDuplicates = { navController.navigate(NavRoute.Duplicates.route) },
                                onNavigateToStorage = { navController.navigate(NavRoute.Storage.route) },
                                onNavigateToTrash = { navController.navigate(NavRoute.Trash.route) },
                                onNavigateToCloudStorageUsage = { navController.navigate(NavRoute.CloudStorageUsage.route) },
                                onNavigateToConnectedServices = { navController.navigate(NavRoute.ConnectedServices.route) },
                                onNavigateToAiAssistant = { navController.navigate(NavRoute.AiAssistant.route) }
                            )
                        }

                        composable(
                            route = NavRoute.MediaViewer.route,
                            arguments = listOf(navArgument("index") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val indexArg = backStackEntry.arguments?.getInt("index") ?: MediaViewerStateHolder.activeViewerIndex
                            val currentList = if (activeViewerList.isNotEmpty()) activeViewerList else com.example.ui.viewer.MediaViewerStateHolder.activeViewerList
                            val shouldPlay = activeViewerAutoPlayVideo || com.example.ui.viewer.MediaViewerStateHolder.activeViewerAutoPlayVideo
                            MediaViewerScreen(
                                mediaList = currentList,
                                initialIndex = indexArg,
                                initialPlayVideo = shouldPlay,
                                mediaRepository = mediaRepository,
                                backupRepository = backupRepository,
                                r2Repository = r2Repository,
                                onItemDeleted = { deletedItem ->
                                    if (deletedItem.isCloud) {
                                        cloudTabViewModel.onItemDeletedLocally(deletedItem)
                                    }
                                },
                                onBack = {
                                    galleryViewModel.loadMedia()
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(NavRoute.Backup.route) {
                            BackupScreen(
                                viewModel = backupViewModel,
                                onNavigateToSettings = { navController.navigate(NavRoute.Settings.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.Cloud.route) {
                            CloudBrowserScreen(
                                viewModel = cloudViewModel,
                                onNavigateToSettings = { navController.navigate(NavRoute.Settings.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.Settings.route) {
                            SettingsScreen(
                                r2Repository = r2Repository,
                                preferencesManager = preferencesManager,
                                storageUsageViewModel = storageUsageViewModel,
                                onNavigateToStorageUsage = { navController.navigate(NavRoute.CloudStorageUsage.route) },
                                onNavigateToSmartCollectionsSettings = { navController.navigate(NavRoute.SmartCollectionsSettings.route) },
                                onNavigateToConnectedServices = { navController.navigate(NavRoute.ConnectedServices.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.ConnectedServices.route) {
                            com.example.ui.settings.ConnectedServicesScreen(
                                viewModel = connectedServicesViewModel,
                                onBack = { navController.popBackStack() },
                                onBrowseProvider = { providerId ->
                                    cloudTabViewModel.setProviderFilter(providerId)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(NavRoute.SmartCollections.route) {
                            com.example.ui.smartcollections.SmartCollectionsScreen(
                                viewModel = smartCollectionsViewModel,
                                onCollectionClick = { collection ->
                                    navController.navigate(NavRoute.SmartCollectionDetail.createRoute(collection.id))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(NavRoute.SmartCollectionsSettings.route)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = NavRoute.SmartCollectionDetail.route,
                            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                            com.example.ui.smartcollections.SmartCollectionDetailScreen(
                                collectionId = collectionId,
                                viewModel = smartCollectionsViewModel,
                                onOpenViewer = { initialIndex, items ->
                                    com.example.ui.viewer.MediaViewerStateHolder.setViewerData(items, initialIndex, false)
                                    activeViewerList = items
                                    activeViewerIndex = initialIndex
                                    activeViewerAutoPlayVideo = false
                                    navController.navigate(NavRoute.MediaViewer.createRoute(initialIndex))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.SmartCollectionsSettings.route) {
                            com.example.ui.smartcollections.SmartCollectionsSettingsScreen(
                                viewModel = smartCollectionsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.CloudStorageUsage.route) {
                            CloudStorageUsageScreen(
                                viewModel = storageUsageViewModel,
                                onNavigateToSettings = { navController.navigate(NavRoute.Settings.route) },
                                onCategoryClick = { category ->
                                    cloudTabViewModel.setCategoryFilter(category)
                                    navController.popBackStack(NavRoute.Gallery.route, inclusive = false)
                                    galleryViewModel.selectTab(com.example.ui.gallery.GalleryTab.CLOUD)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.Duplicates.route) {
                            DuplicateFinderScreen(
                                mediaRepository = mediaRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.Storage.route) {
                            StorageAnalyzerScreen(
                                mediaRepository = mediaRepository,
                                r2Repository = r2Repository,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.Trash.route) {
                            TrashScreen(
                                mediaRepository = mediaRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoute.AiAssistant.route) {
                            com.example.ui.ai.AiAssistantScreen(
                                viewModel = aiAssistantViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenViewer = { initialIndex, items, autoPlayVideo ->
                                    com.example.ui.viewer.MediaViewerStateHolder.setViewerData(items, initialIndex, autoPlayVideo)
                                    activeViewerList = items
                                    activeViewerIndex = initialIndex
                                    activeViewerAutoPlayVideo = autoPlayVideo
                                    navController.navigate(NavRoute.MediaViewer.createRoute(initialIndex))
                                },
                                onNavigateToRoute = { route ->
                                    when (route) {
                                        "gallery" -> navController.navigate(NavRoute.Gallery.route)
                                        "backup" -> navController.navigate(NavRoute.Backup.route)
                                        "cloud" -> navController.navigate(NavRoute.Cloud.route)
                                        "settings" -> navController.navigate(NavRoute.Settings.route)
                                        "duplicates" -> navController.navigate(NavRoute.Duplicates.route)
                                        "storage" -> navController.navigate(NavRoute.Storage.route)
                                        "cloud_storage_usage" -> navController.navigate(NavRoute.CloudStorageUsage.route)
                                        "trash" -> navController.navigate(NavRoute.Trash.route)
                                        "smart_collections" -> navController.navigate(NavRoute.SmartCollections.route)
                                        "smart_collections_settings" -> navController.navigate(NavRoute.SmartCollectionsSettings.route)
                                        "connected_services" -> navController.navigate(NavRoute.ConnectedServices.route)
                                        else -> {
                                            try {
                                                navController.navigate(route)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
