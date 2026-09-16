package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CloudQueue
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.multicloud.CloudCapabilities
import com.example.domain.model.multicloud.CloudConnectionState
import com.example.domain.model.multicloud.CloudOperation
import com.example.domain.model.multicloud.ProviderConnectionInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedServicesScreen(
    viewModel: ConnectedServicesViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                title = { Text("Connected Services", fontWeight = FontWeight.Bold) },
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
                            contentDescription = "Refresh providers"
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
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Security Guarantee Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Direct & Secure Multi-Cloud",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "CloudGallery connects directly from your device to official cloud APIs. Your tokens and keys are encrypted via AndroidKeyStore AES-256 and never shared with any developer backend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Providers List
            items(uiState.providers, key = { it.providerId }) { info ->
                ProviderCard(
                    info = info,
                    onConnect = { viewModel.openConfigure(info.providerId) },
                    onDisconnect = { viewModel.requestDisconnect(info) },
                    onViewCapabilities = { viewModel.openCapabilities(info) }
                )
            }
        }
    }

    // Configure R2 Dialog
    if (uiState.selectedProviderForConfig == "r2") {
        ConfigureR2Dialog(
            onDismiss = { viewModel.closeConfigure() },
            onConnect = { accId, keyId, secret, bucket, ep ->
                viewModel.connectR2(accId, keyId, secret, bucket, ep)
            }
        )
    }

    // Configure OAuth Dialog (Google Photos, OneDrive, Dropbox)
    if (uiState.selectedProviderForConfig != null && uiState.selectedProviderForConfig != "r2") {
        val provId = uiState.selectedProviderForConfig!!
        val providerInfo = uiState.providers.find { it.providerId == provId }
        val displayName = providerInfo?.displayName ?: provId

        ConfigureOAuthDialog(
            providerId = provId,
            displayName = displayName,
            onDismiss = { viewModel.closeConfigure() },
            onDirectConnect = { token, refresh, client, email, name ->
                viewModel.connectOAuthProvider(provId, token, refresh, client, email, name)
            },
            onLaunchBrowserAuth = { clientId ->
                val verifier = viewModel.generateCodeVerifier()
                val challenge = viewModel.generateCodeChallenge(verifier)
                val url = viewModel.buildAuthUrl(provId, clientId, challenge)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback if no browser
                }
            },
            onExchangeCode = { code, clientId, verifier, secret ->
                viewModel.exchangeOAuthCode(provId, code, clientId, verifier, secret)
            },
            generateVerifier = { viewModel.generateCodeVerifier() },
            generateChallenge = { v -> viewModel.generateCodeChallenge(v) }
        )
    }

    // Disconnect Confirmation Dialog
    uiState.providerToDisconnect?.let { toDisconnect ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDisconnect() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Disconnect ${toDisconnect.displayName}?") },
            text = {
                Text("This will remove CloudGallery's access to ${toDisconnect.displayName}. Your files on ${toDisconnect.displayName} will not be deleted, and local gallery photos remain untouched.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDisconnect(toDisconnect) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDisconnect() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Capabilities Dialog
    uiState.selectedProviderForCapabilities?.let { info ->
        CapabilitiesDialog(
            info = info,
            onDismiss = { viewModel.closeCapabilities() }
        )
    }
}

@Composable
private fun ProviderCard(
    info: ProviderConnectionInfo,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onViewCapabilities: () -> Unit
) {
    val isConnected = info.connectionState.isConnected

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${info.providerId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when (info.providerId) {
                            "r2" -> Color(0xFFF6821F).copy(alpha = 0.15f)
                            "google_photos" -> Color(0xFF4285F4).copy(alpha = 0.15f)
                            "onedrive" -> Color(0xFF0078D4).copy(alpha = 0.15f)
                            "dropbox" -> Color(0xFF0061FF).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = info.displayName,
                                tint = when (info.providerId) {
                                    "r2" -> Color(0xFFE56A07)
                                    "google_photos" -> Color(0xFF1967D2)
                                    "onedrive" -> Color(0xFF0078D4)
                                    "dropbox" -> Color(0xFF0061FF)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (info.providerId) {
                                "r2" -> "S3-Compatible Cloud Storage"
                                "google_photos" -> "Official Google Photos Library"
                                "onedrive" -> "Microsoft Graph Storage"
                                "dropbox" -> "Dropbox API v2"
                                else -> "Cloud Provider"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connection status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) Color(0xFF2E7D32).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color(0xFF2E7D32) else Color.Gray,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status / account info details
            if (isConnected) {
                val conn = info.connectionState as? CloudConnectionState.Connected
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    conn?.accountName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    conn?.accountEmail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Storage Quota info if available
                    info.storageQuota?.let { quota ->
                        val usedStr = quota.usedBytes?.let { formatBytes(it) }
                        val totalStr = quota.totalBytes?.let { formatBytes(it) }
                        if (usedStr != null && totalStr != null) {
                            Text(
                                text = "Storage: $usedStr of $totalStr used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (usedStr != null) {
                            Text(
                                text = "Storage used: $usedStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (!quota.isAvailable && quota.statusMessage != null) {
                            Text(
                                text = quota.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewCapabilities,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Capabilities", fontSize = 13.sp)
                }

                if (isConnected) {
                    FilledTonalButton(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Manage", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text("Disconnect", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Connect", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureR2Dialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, String, String) -> Unit
) {
    var accountId by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloudflare R2 Storage") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Enter your personal Cloudflare R2 S3 credentials. Secrets are stored in hardware-backed Android Keystore.",
                    style = MaterialTheme.typography.bodySmall
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
private fun ConfigureOAuthDialog(
    providerId: String,
    displayName: String,
    onDismiss: () -> Unit,
    onDirectConnect: (token: String, refresh: String?, client: String?, email: String?, name: String?) -> Unit,
    onLaunchBrowserAuth: (clientId: String) -> Unit,
    onExchangeCode: (code: String, clientId: String, verifier: String, secret: String?) -> Unit,
    generateVerifier: () -> String,
    generateChallenge: (String) -> String
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Direct Token, 1: Browser OAuth

    var accessToken by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var accountEmail by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }

    var authCode by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var savedVerifier by remember { mutableStateOf(generateVerifier()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect $displayName") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Direct Token", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("OAuth / Browser", fontSize = 13.sp) }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        text = "Provide your personal developer token or API access token. It is encrypted in Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = refreshToken,
                        onValueChange = { refreshToken = it },
                        label = { Text("Refresh Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("Client ID (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("Account Email / Label (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Authorize via the official $displayName login portal using PKCE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("OAuth Client ID") },
                        placeholder = { Text("Enter OAuth Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onLaunchBrowserAuth(clientId) },
                        enabled = clientId.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open $displayName Login")
                    }

                    OutlinedTextField(
                        value = authCode,
                        onValueChange = { authCode = it },
                        label = { Text("Authorization Code / Callback URL") },
                        placeholder = { Text("Paste code or callback URL here") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = { Text("Client Secret (If required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onDirectConnect(
                            accessToken,
                            refreshToken.takeIf { it.isNotBlank() },
                            clientId.takeIf { it.isNotBlank() },
                            accountEmail.takeIf { it.isNotBlank() },
                            accountName.takeIf { it.isNotBlank() }
                        )
                    } else {
                        // Extract code from URL if full URL is pasted
                        val cleanCode = if (authCode.contains("code=")) {
                            Uri.parse(authCode).getQueryParameter("code") ?: authCode
                        } else {
                            authCode
                        }
                        onExchangeCode(cleanCode, clientId, savedVerifier, clientSecret.takeIf { it.isNotBlank() })
                    }
                },
                enabled = if (selectedTab == 0) accessToken.isNotEmpty() else authCode.isNotEmpty() && clientId.isNotEmpty()
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
private fun CapabilitiesDialog(
    info: ProviderConnectionInfo,
    onDismiss: () -> Unit
) {
    val caps = info.capabilities

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${info.displayName} Capabilities") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CapabilityRow("Browse & List Media", caps.canBrowse)
                CapabilityRow("Search Media", caps.canSearch)
                CapabilityRow("Download High-Res Media", caps.canDownload)
                CapabilityRow("Upload Media", caps.canUpload)
                CapabilityRow(
                    label = "Delete Files",
                    supported = caps.canDelete,
                    note = if (!caps.canDelete) caps.getUnsupportedReason(CloudOperation.DELETE, info.displayName) else null
                )
                CapabilityRow(
                    label = "Rename / Move",
                    supported = caps.canRename,
                    note = if (!caps.canRename) "Not supported by this provider" else null
                )
                CapabilityRow(
                    label = "Arbitrary Folders",
                    supported = caps.canCreateFolder,
                    note = if (!caps.canCreateFolder) "Flat or album-based collection" else null
                )
                CapabilityRow(
                    label = "Storage Quota Tracking",
                    supported = caps.supportsStorageUsage,
                    note = if (!caps.supportsStorageUsage) caps.getUnsupportedReason(CloudOperation.STORAGE_USAGE, info.displayName) else null
                )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (supported) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (supported) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (!note.isNullOrEmpty()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups.coerceIn(0, units.size - 1)]
    )
}
