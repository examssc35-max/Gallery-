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

class MainActivity : ComponentActivity() {

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
        val cloudTabViewModel = CloudTabViewModel(r2Repository, backupRepository, preferencesManager)
        val getStorageUsageUseCase = GetStorageUsageUseCase(r2Repository)
        val storageUsageViewModel = StorageUsageViewModel(getStorageUsageUseCase, r2Repository)

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

                    NavHost(
                        navController = navController,
                        startDestination = NavRoute.Gallery.route
                    ) {
                        composable(NavRoute.Gallery.route) {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                r2Repository = r2Repository,
                                cloudTabViewModel = cloudTabViewModel,
                                onOpenViewer = { index, items ->
                                    activeViewerList = items
                                    activeViewerIndex = index
                                    navController.navigate(NavRoute.MediaViewer.createRoute(index))
                                },
                                onNavigateToBackup = { navController.navigate(NavRoute.Backup.route) },
                                onNavigateToCloud = { navController.navigate(NavRoute.Cloud.route) },
                                onNavigateToSettings = { navController.navigate(NavRoute.Settings.route) },
                                onNavigateToDuplicates = { navController.navigate(NavRoute.Duplicates.route) },
                                onNavigateToStorage = { navController.navigate(NavRoute.Storage.route) },
                                onNavigateToTrash = { navController.navigate(NavRoute.Trash.route) },
                                onNavigateToCloudStorageUsage = { navController.navigate(NavRoute.CloudStorageUsage.route) }
                            )
                        }

                        composable(
                            route = NavRoute.MediaViewer.route,
                            arguments = listOf(navArgument("index") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val indexArg = backStackEntry.arguments?.getInt("index") ?: activeViewerIndex
                            MediaViewerScreen(
                                mediaList = activeViewerList,
                                initialIndex = indexArg,
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
                    }
                }
            }
        }
    }
}
