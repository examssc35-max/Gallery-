package com.example.ui.settings

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.ProviderConnectionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedServicesScreen(
    viewModel: ConnectedServicesViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Storage & Services", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadProviders() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh status"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Security Guarantee Banner
            item {
                SecurityNoticeCard()
            }

            // Providers Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Supported Cloud Providers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Provider Cards List
            items(uiState.providers, key = { it.providerId }) { info ->
                OfficialProviderCard(
                    info = info,
                    onConnectClick = { viewModel.openConfigure(info.providerId) },
                    onDisconnectClick = { viewModel.requestDisconnect(info) },
                    onCapabilitiesClick = { viewModel.openCapabilities(info) },
                    onQuickOfficialAuth = { providerId ->
                        viewModel.startOfficialAuth(context, providerId)
                    }
                )
            }

            // Architecture Footer
            item {
                ArchitectureDetailsCard()
            }
        }
    }

    // Provider Configuration Dialogs
    val activeConfigProvider = uiState.selectedProviderForConfig
    if (activeConfigProvider != null) {
        when (activeConfigProvider) {
            "r2" -> {
                ConfigureR2Dialog(
                    onDismiss = { viewModel.closeConfigure() },
                    onConnect = { acc, key, sec, buck, ep ->
                        viewModel.connectR2(acc, key, sec, buck, ep)
                    }
                )
            }
            "google_photos" -> {
                OfficialOAuthConnectDialog(
                    providerId = "google_photos",
                    providerName = "Google Photos",
                    serviceDescription = "Google Photos is personal photo and video storage. CloudGallery connects directly using official Google OAuth to view and backup your photos.",
                    buttonLabel = "Continue with Google",
                    buttonBrandColor = Color(0xFF4285F4),
                    buttonTextColor = Color.White,
                    icon = Icons.Default.PhotoLibrary,
                    onDismiss = { viewModel.closeConfigure() },
                    onStartAuth = { customClientId ->
                        viewModel.startOfficialAuth(context, "google_photos", customClientId)
                    },
                    onManualCode = { code, customClientId ->
                        viewModel.exchangeManualCode("google_photos", code, customClientId)
                    }
                )
            }
            "google_drive" -> {
                OfficialOAuthConnectDialog(
                    providerId = "google_drive",
                    providerName = "Google Drive",
                    serviceDescription = "Google Drive provides cloud file and document storage. CloudGallery connects via official Google OAuth to browse and manage your photos and albums.",
                    buttonLabel = "Continue with Google",
                    buttonBrandColor = Color(0xFF0F9D58),
                    buttonTextColor = Color.White,
                    icon = Icons.Default.Folder,
                    onDismiss = { viewModel.closeConfigure() },
                    onStartAuth = { customClientId ->
                        viewModel.startOfficialAuth(context, "google_drive", customClientId)
                    },
                    onManualCode = { code, customClientId ->
                        viewModel.exchangeManualCode("google_drive", code, customClientId)
                    }
                )
            }
            "onedrive" -> {
                OfficialOAuthConnectDialog(
                    providerId = "onedrive",
                    providerName = "Microsoft OneDrive",
                    serviceDescription = "Microsoft OneDrive connects via official Microsoft identity authorization. CloudGallery interacts directly with Microsoft Graph API from your device.",
                    buttonLabel = "Continue with Microsoft",
                    buttonBrandColor = Color(0xFF0078D4),
                    buttonTextColor = Color.White,
                    icon = Icons.Default.Cloud,
                    onDismiss = { viewModel.closeConfigure() },
                    onStartAuth = { customClientId ->
                        viewModel.startOfficialAuth(context, "onedrive", customClientId)
                    },
                    onManualCode = { code, customClientId ->
                        viewModel.exchangeManualCode("onedrive", code, customClientId)
                    }
                )
            }
            "dropbox" -> {
                OfficialOAuthConnectDialog(
                    providerId = "dropbox",
                    providerName = "Dropbox",
                    serviceDescription = "Dropbox connects via official Dropbox OAuth authorization. Tokens are generated through PKCE on this device and saved into Android Keystore.",
                    buttonLabel = "Continue with Dropbox",
                    buttonBrandColor = Color(0xFF0061FF),
                    buttonTextColor = Color.White,
                    icon = Icons.Default.Storage,
                    onDismiss = { viewModel.closeConfigure() },
                    onStartAuth = { customClientId ->
                        viewModel.startOfficialAuth(context, "dropbox", customClientId)
                    },
                    onManualCode = { code, customClientId ->
                        viewModel.exchangeManualCode("dropbox", code, customClientId)
                    }
                )
            }
        }
    }

    // Disconnect Confirmation Dialog
    uiState.providerToDisconnect?.let { info ->
        DisconnectConfirmDialog(
            info = info,
            onDismiss = { viewModel.dismissDisconnect() },
            onConfirm = { viewModel.confirmDisconnect(info) }
        )
    }

    // Capabilities Details Dialog
    uiState.selectedProviderForCapabilities?.let { info ->
        ProviderCapabilitiesDialog(
            info = info,
            onDismiss = { viewModel.closeCapabilities() }
        )
    }
}

@Composable
private fun SecurityNoticeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Client-Only Direct Cloud Access",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CloudGallery connects directly from your device to each cloud provider. No developer backend, no proxy servers, and no third-party tracking. All credentials and tokens are encrypted with hardware-backed Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun OfficialProviderCard(
    info: ProviderConnectionInfo,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onCapabilitiesClick: () -> Unit,
    onQuickOfficialAuth: (String) -> Unit
) {
    val isConnected = info.connectionState.isConnected
    val providerTheme = getProviderTheme(info.providerId)

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${info.providerId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon, Name, Category, Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(providerTheme.brandColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = providerTheme.icon,
                        contentDescription = info.displayName,
                        tint = providerTheme.brandColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = providerTheme.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ConnectionStatusBadge(info.connectionState)
            }

            // Connection Details or Connected Account Info
            when (val state = info.connectionState) {
                is CloudConnectionState.Connected -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!state.accountEmail.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Account: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = state.accountEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (!state.accountName.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Account: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = state.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Storage Quota Bar if available
                            val quota = info.storageQuota
                            val totalBytes = quota?.totalBytes
                            val usedBytes = quota?.usedBytes ?: 0L
                            if (quota != null && quota.isAvailable && totalBytes != null && totalBytes > 0L) {
                                val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
                                val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                                val progress = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Storage Used",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format("%.1f GB / %.1f GB", usedGb, totalGb),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = providerTheme.brandColor
                                    )
                                }
                            } else if (quota != null && !quota.statusMessage.isNullOrEmpty()) {
                                Text(
                                    text = quota.statusMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is CloudConnectionState.AuthRequired -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> {
                    // Not connected hint
                    Text(
                        text = providerTheme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected) {
                    TextButton(
                        onClick = onCapabilitiesClick,
                        modifier = Modifier.testTag("btn_caps_${info.providerId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Capabilities")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onDisconnectClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("btn_disconnect_${info.providerId}")
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    if (info.providerId == "r2") {
                        Button(
                            onClick = onConnectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = providerTheme.brandColor
                            ),
                            modifier = Modifier.testTag("btn_connect_${info.providerId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure R2")
                        }
                    } else {
                        // Official Continue with [Provider] Button
                        Button(
                            onClick = onConnectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = providerTheme.brandColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_continue_${info.providerId}")
                        ) {
                            Icon(
                                imageVector = providerTheme.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = providerTheme.continueButtonLabel,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBadge(state: CloudConnectionState) {
    val (label, containerColor, contentColor, icon) = when (state) {
        is CloudConnectionState.Connected -> {
            Tuple4("Connected", Color(0xFFE6F4EA), Color(0xFF137333), Icons.Default.CheckCircle)
        }
        is CloudConnectionState.Connecting -> {
            Tuple4("Connecting...", Color(0xFFFEF7E0), Color(0xFFB06000), Icons.Default.Refresh)
        }
        is CloudConnectionState.Refreshing -> {
            Tuple4("Syncing...", Color(0xFFE8F0FE), Color(0xFF1A73E8), Icons.Default.Refresh)
        }
        is CloudConnectionState.AuthRequired -> {
            Tuple4("Auth Required", Color(0xFFFCE8E6), Color(0xFFC5221F), Icons.Default.Lock)
        }
        is CloudConnectionState.PermissionRequired -> {
            Tuple4("Permissions Needed", Color(0xFFFCE8E6), Color(0xFFC5221F), Icons.Default.Warning)
        }
        is CloudConnectionState.Offline -> {
            Tuple4("Offline", Color(0xFFF1F3F4), Color(0xFF5F6368), Icons.Default.CloudOff)
        }
        is CloudConnectionState.Error -> {
            Tuple4("Error", Color(0xFFFCE8E6), Color(0xFFC5221F), Icons.Default.Close)
        }
        else -> {
            Tuple4("Not Connected", Color(0xFFF1F3F4), Color(0xFF5F6368), Icons.Default.CloudOff)
        }
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

/**
 * Official OAuth Sign-in Dialog for Google Photos, Google Drive, OneDrive, and Dropbox.
 * No generic token fields. Clean 'Continue with [Provider]' primary action.
 */
@Composable
private fun OfficialOAuthConnectDialog(
    providerId: String,
    providerName: String,
    serviceDescription: String,
    buttonLabel: String,
    buttonBrandColor: Color,
    buttonTextColor: Color,
    icon: ImageVector,
    onDismiss: () -> Unit,
    onStartAuth: (customClientId: String?) -> Unit,
    onManualCode: (code: String, customClientId: String?) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var customClientId by remember { mutableStateOf("") }
    var showManualCode by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(buttonBrandColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = buttonBrandColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Connect $providerName")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = serviceDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "CloudGallery uses official OAuth 2.0 PKCE. Your credentials are processed directly by $providerName and tokens are stored in Android Keystore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Primary Official Sign-In Button
                Button(
                    onClick = {
                        onStartAuth(customClientId.takeIf { it.isNotBlank() })
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBrandColor,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_dialog_continue_$providerId")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Expandable Custom Client ID for Developers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Custom Client ID (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Enter a custom OAuth Client ID if you prefer using your own Google Cloud / Microsoft Azure / Dropbox App Console project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customClientId,
                            onValueChange = { customClientId = it },
                            label = { Text("Client ID / App Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Expandable Manual Code Entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualCode = !showManualCode }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Manual Authorization Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (showManualCode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showManualCode) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "If your browser displayed an authorization code or callback redirect rather than returning automatically, paste it here:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = manualCodeInput,
                            onValueChange = { manualCodeInput = it },
                            label = { Text("Authorization Code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onManualCode(manualCodeInput.trim(), customClientId.takeIf { it.isNotBlank() })
                            },
                            enabled = manualCodeInput.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Submit Code")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfigureR2Dialog(
    onDismiss: () -> Unit,
    onConnect: (accountId: String, accessKeyId: String, secretAccessKey: String, bucketName: String, endpoint: String) -> Unit
) {
    var accountId by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF38020).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFFF38020),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Cloudflare R2 Storage")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter your personal Cloudflare R2 S3 credentials. Keys are encrypted in hardware-backed Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = accountId,
                    onValueChange = { accountId = it },
                    label = { Text("Account ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text("Access Key ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretAccessKey,
                    onValueChange = { secretAccessKey = it },
                    label = { Text("Secret Access Key") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bucketName,
                    onValueChange = { bucketName = it },
                    label = { Text("Bucket Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Custom Endpoint (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(accountId, accessKeyId, secretAccessKey, bucketName, endpoint)
                },
                enabled = accessKeyId.isNotEmpty() && secretAccessKey.isNotEmpty() && bucketName.isNotEmpty()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DisconnectConfirmDialog(
    info: ProviderConnectionInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Disconnect ${info.displayName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Disconnecting will remove authorization and credentials from this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 Safe Disconnect: None of your photos, albums, or files in ${info.displayName} will be deleted or modified. CloudGallery only disconnects this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProviderCapabilitiesDialog(
    info: ProviderConnectionInfo,
    onDismiss: () -> Unit
) {
    val caps = info.capabilities

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${info.displayName} Capabilities") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Operations supported by ${info.displayName} API:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CapabilityRow("Browse / Search Media", caps.canBrowse && caps.canSearch)
                CapabilityRow("Download Full Resolution", caps.canDownload)
                CapabilityRow("Upload New Photos", caps.canUpload)
                CapabilityRow(
                    label = "Delete Remote Media",
                    supported = caps.canDelete,
                    note = if (!caps.canDelete) "Google Photos API restricts deletion by third-party apps for user safety" else null
                )
                CapabilityRow("Storage Quota Monitoring", caps.supportsStorageUsage)
                CapabilityRow("Background Sync", caps.supportsBackgroundSync)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CapabilityRow(
    label: String,
    supported: Boolean,
    note: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (supported) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (supported) Color(0xFF137333) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (!note.isNullOrEmpty()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }
}

@Composable
private fun ArchitectureDetailsCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Multi-Cloud Privacy & Security",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "• Google Photos: Official Google sign-in for photo library read & backup.\n" +
                        "• Google Drive: Official Google sign-in for cloud drive file browsing.\n" +
                        "• Microsoft OneDrive: Official Microsoft Graph authorization.\n" +
                        "• Dropbox: Official Dropbox PKCE OAuth authorization.\n" +
                        "• Cloudflare R2: Direct S3 API connection with zero egress fees.\n" +
                        "• Disconnecting any service keeps your cloud files intact.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

private data class ProviderTheme(
    val icon: ImageVector,
    val brandColor: Color,
    val subtitle: String,
    val description: String,
    val continueButtonLabel: String
)

private fun getProviderTheme(providerId: String): ProviderTheme {
    return when (providerId) {
        "r2" -> ProviderTheme(
            icon = Icons.Default.Storage,
            brandColor = Color(0xFFF38020),
            subtitle = "Cloudflare R2 Object Storage",
            description = "High-speed S3-compatible cloud storage with zero egress fees. Configured with direct API credentials.",
            continueButtonLabel = "Configure R2"
        )
        "google_photos" -> ProviderTheme(
            icon = Icons.Default.PhotoLibrary,
            brandColor = Color(0xFF4285F4),
            subtitle = "Google Photos Library",
            description = "Personal photo and video library. Connects directly using official Google sign-in.",
            continueButtonLabel = "Continue with Google"
        )
        "google_drive" -> ProviderTheme(
            icon = Icons.Default.Folder,
            brandColor = Color(0xFF0F9D58),
            subtitle = "Google Drive Cloud Storage",
            description = "Cloud drive files and folders. Connects directly using official Google sign-in.",
            continueButtonLabel = "Continue with Google"
        )
        "onedrive" -> ProviderTheme(
            icon = Icons.Default.Cloud,
            brandColor = Color(0xFF0078D4),
            subtitle = "Microsoft OneDrive",
            description = "Microsoft cloud storage. Connects directly using official Microsoft sign-in.",
            continueButtonLabel = "Continue with Microsoft"
        )
        "dropbox" -> ProviderTheme(
            icon = Icons.Default.Storage,
            brandColor = Color(0xFF0061FF),
            subtitle = "Dropbox Cloud Storage",
            description = "Dropbox photo and media storage. Connects directly using official Dropbox authorization.",
            continueButtonLabel = "Continue with Dropbox"
        )
        else -> ProviderTheme(
            icon = Icons.Default.Cloud,
            brandColor = Color(0xFF5F6368),
            subtitle = "Cloud Storage Provider",
            description = "Third-party cloud storage service.",
            continueButtonLabel = "Continue"
        )
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
