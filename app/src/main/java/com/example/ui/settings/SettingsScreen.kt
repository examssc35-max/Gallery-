package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppThemeMode
import com.example.data.local.PreferencesManager
import com.example.data.local.R2Credentials
import com.example.data.local.SortOrder
import com.example.domain.repository.R2Repository
import com.example.ui.storage.StorageUsageViewModel
import com.example.ui.theme.DynamicAppBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    r2Repository: R2Repository,
    preferencesManager: PreferencesManager,
    storageUsageViewModel: StorageUsageViewModel? = null,
    onNavigateToStorageUsage: () -> Unit = {},
    onNavigateToSmartCollectionsSettings: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToCloudStorage: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToAiAssistant: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    val savedCredentials by r2Repository.credentialsFlow.collectAsState(initial = R2Credentials())
    val isR2Connected = savedCredentials.secretAccessKey.isNotEmpty() && savedCredentials.bucketName.isNotEmpty()

    val themeMode by preferencesManager.themeModeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
    val gridColumns by preferencesManager.gridColumnsFlow.collectAsState(initial = 3)
    val sortOrder by preferencesManager.sortOrderFlow.collectAsState(initial = SortOrder.DATE_DESC)

    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    DynamicAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section: Personalization & Display
                item {
                    SettingsSectionCard(title = "Personalization") {
                        SettingsRow(
                            icon = Icons.Default.Palette,
                            iconColor = Color(0xFF7C4DFF),
                            title = "Theme & Appearance",
                            subtitle = "${themeMode.name.lowercase().replaceFirstChar { it.uppercase() }} • Custom backgrounds & accents",
                            onClick = onNavigateToTheme
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsRow(
                            icon = Icons.Default.GridView,
                            iconColor = Color(0xFF1976D2),
                            title = "Gallery Grid & Sorting",
                            subtitle = "$gridColumns columns • ${when (sortOrder) {
                                SortOrder.DATE_DESC -> "Newest first"
                                SortOrder.DATE_ASC -> "Oldest first"
                                SortOrder.NAME_ASC -> "Name A-Z"
                                SortOrder.NAME_DESC -> "Name Z-A"
                                SortOrder.SIZE_DESC -> "Largest size"
                            }}",
                            onClick = { showAppearanceDialog = true }
                        )
                    }
                }

                // Section: Cloud & Sync
                item {
                    SettingsSectionCard(title = "Cloud & Backup") {
                        SettingsRow(
                            icon = Icons.Default.Cloud,
                            iconColor = Color(0xFFF6821F),
                            title = "Cloudflare R2",
                            subtitle = if (isR2Connected) "Connected • ${savedCredentials.bucketName}" else "Not connected • Zero egress storage",
                            onClick = onNavigateToCloudStorage
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsRow(
                            icon = Icons.Default.Backup,
                            iconColor = Color(0xFF10B981),
                            title = "Automatic Backup",
                            subtitle = "Background sync to Cloudflare R2",
                            onClick = onNavigateToBackup
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsRow(
                            icon = Icons.Default.PieChart,
                            iconColor = Color(0xFF00897B),
                            title = "Storage Usage",
                            subtitle = "Inspect breakdown of photos, videos & files",
                            onClick = onNavigateToStorageUsage
                        )
                    }
                }

                // Section: Smart Tools & AI
                item {
                    SettingsSectionCard(title = "Smart Intelligence") {
                        SettingsRow(
                            icon = Icons.Default.AutoAwesome,
                            iconColor = Color(0xFFEC4899),
                            title = "AI Assistant",
                            subtitle = "Intelligent natural language search & actions",
                            onClick = onNavigateToAiAssistant
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsRow(
                            icon = Icons.Default.Collections,
                            iconColor = Color(0xFF8B5CF6),
                            title = "Smart Collections",
                            subtitle = "Automatic on-device scene & object grouping",
                            onClick = onNavigateToSmartCollectionsSettings
                        )
                    }
                }

                // Section: System & About
                item {
                    SettingsSectionCard(title = "General") {
                        SettingsRow(
                            icon = Icons.Default.Security,
                            iconColor = Color(0xFF64748B),
                            title = "Privacy & Security",
                            subtitle = "Hardware Keystore encryption • Local-first",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsRow(
                            icon = Icons.Default.Info,
                            iconColor = Color(0xFF0284C7),
                            title = "About CloudGallery",
                            subtitle = "Version 1.0 • Modern Cloudflare R2 Gallery",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Appearance Dialog (Grid columns & sort order)
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Gallery Grid & Sorting") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Grid Columns", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5).forEach { cols ->
                            FilterChip(
                                selected = gridColumns == cols,
                                onClick = {
                                    scope.launch { preferencesManager.setGridColumns(cols) }
                                },
                                label = { Text("$cols") }
                            )
                        }
                    }

                    Text("Sort Order", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SortOrder.values().forEach { order ->
                            val label = when (order) {
                                SortOrder.DATE_DESC -> "Date (Newest First)"
                                SortOrder.DATE_ASC -> "Date (Oldest First)"
                                SortOrder.NAME_ASC -> "Name (A to Z)"
                                SortOrder.NAME_DESC -> "Name (Z to A)"
                                SortOrder.SIZE_DESC -> "Size (Largest First)"
                            }
                            FilterChip(
                                selected = sortOrder == order,
                                onClick = {
                                    scope.launch { preferencesManager.setSortOrder(order) }
                                },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Hardware Keystore: Your Cloudflare R2 secret access keys are encrypted with AES-256-GCM using hardware keys generated inside the Android Keystore.")
                    Text("• Zero Tracking: No analytics, tracking, or advertisements are present.")
                    Text("• Direct R2: All file transfers communicate directly between your device and your personal Cloudflare R2 bucket.")
                    Text("• Local-First: Media metadata is saved securely in a local SQLite Room database on your device.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About CloudGallery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CloudGallery v1.0", fontWeight = FontWeight.Bold)
                    Text("Modern Android Photo & Video Gallery with seamless Cloudflare R2 personal cloud storage integration.")
                    Text("Designed with Material Design 3 and fluid iOS-inspired aesthetics.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}
