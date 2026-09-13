package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppThemeMode
import com.example.data.local.PreferencesManager
import com.example.data.local.R2Credentials
import com.example.data.local.SortOrder
import com.example.domain.repository.R2Repository
import com.example.ui.storage.StorageUsageSettingsSection
import com.example.ui.storage.StorageUsageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    r2Repository: R2Repository,
    preferencesManager: PreferencesManager,
    storageUsageViewModel: StorageUsageViewModel? = null,
    onNavigateToStorageUsage: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedCredentials by r2Repository.credentialsFlow.collectAsState(initial = R2Credentials())
    val gridColumns by preferencesManager.gridColumnsFlow.collectAsState(initial = 3)
    val sortOrder by preferencesManager.sortOrderFlow.collectAsState(initial = SortOrder.DATE_DESC)
    val themeMode by preferencesManager.themeModeFlow.collectAsState(initial = AppThemeMode.SYSTEM)

    var accountId by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var showSecretKey by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }

    // Initialize local input fields when credentials load
    LaunchedEffect(savedCredentials) {
        if (accountId.isEmpty()) accountId = savedCredentials.accountId
        if (accessKeyId.isEmpty()) accessKeyId = savedCredentials.accessKeyId
        if (secretAccessKey.isEmpty()) secretAccessKey = savedCredentials.secretAccessKey
        if (bucketName.isEmpty()) bucketName = savedCredentials.bucketName
        if (endpoint.isEmpty()) endpoint = savedCredentials.endpoint
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
            // 1. Cloudflare R2 Credentials Card
            item {
                Text(
                    text = "Cloudflare R2 Storage (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your own R2 credentials for direct personal backup. Keys are stored in hardware-backed Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = accountId,
                            onValueChange = { accountId = it },
                            label = { Text("Account ID") },
                            placeholder = { Text("e.g. 7a8b9c0d1e2f3a4b...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("r2_account_id_input")
                        )

                        OutlinedTextField(
                            value = accessKeyId,
                            onValueChange = { accessKeyId = it },
                            label = { Text("Access Key ID") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("r2_access_key_input")
                        )

                        OutlinedTextField(
                            value = secretAccessKey,
                            onValueChange = { secretAccessKey = it },
                            label = { Text("Secret Access Key") },
                            singleLine = true,
                            visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showSecretKey = !showSecretKey }) {
                                    Icon(
                                        imageVector = if (showSecretKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("r2_secret_key_input")
                        )

                        OutlinedTextField(
                            value = bucketName,
                            onValueChange = { bucketName = it },
                            label = { Text("Bucket Name") },
                            placeholder = { Text("e.g. my-gallery-backup") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("r2_bucket_input")
                        )

                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Custom Endpoint (Optional)") },
                            placeholder = { Text("Leave blank for default R2 endpoint") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("r2_endpoint_input")
                        )

                        // Connection test result feedback
                        if (testResultMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (testResultSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (testResultSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = testResultMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (testResultSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isTestingConnection = true
                                    testResultMessage = null
                                    scope.launch {
                                        val creds = R2Credentials(
                                            accountId = accountId.trim(),
                                            accessKeyId = accessKeyId.trim(),
                                            secretAccessKey = secretAccessKey.trim(),
                                            bucketName = bucketName.trim(),
                                            endpoint = endpoint.trim()
                                        )
                                        val res = r2Repository.testConnection(creds)
                                        isTestingConnection = false
                                        if (res.isSuccess) {
                                            testResultSuccess = true
                                            testResultMessage = "Connection verified successfully!"
                                        } else {
                                            testResultSuccess = false
                                            testResultMessage = res.exceptionOrNull()?.message ?: "Connection failed"
                                        }
                                    }
                                },
                                enabled = !isTestingConnection && accountId.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucketName.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("r2_test_button")
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Test Connection")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val creds = R2Credentials(
                                            accountId = accountId.trim(),
                                            accessKeyId = accessKeyId.trim(),
                                            secretAccessKey = secretAccessKey.trim(),
                                            bucketName = bucketName.trim(),
                                            endpoint = endpoint.trim(),
                                            isVerified = testResultSuccess == true || savedCredentials.isVerified
                                        )
                                        r2Repository.saveCredentials(creds)
                                        snackbarHostState.showSnackbar("R2 credentials saved securely")
                                    }
                                },
                                enabled = accountId.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucketName.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("r2_save_button")
                            ) {
                                Text("Save Keys")
                            }
                        }

                        if (savedCredentials.secretAccessKey.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        r2Repository.disconnect()
                                        accountId = ""
                                        accessKeyId = ""
                                        secretAccessKey = ""
                                        bucketName = ""
                                        endpoint = ""
                                        testResultMessage = null
                                        testResultSuccess = null
                                        snackbarHostState.showSnackbar("Disconnected and credentials cleared")
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("r2_disconnect_button")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disconnect / Clear Credentials")
                            }
                        }
                    }
                }
            }

            // Storage Usage Section under Cloudflare R2
            if (storageUsageViewModel != null) {
                item {
                    StorageUsageSettingsSection(
                        viewModel = storageUsageViewModel,
                        onViewDetailsClick = onNavigateToStorageUsage
                    )
                }
            }

            // 2. Gallery Preferences
            item {
                Text(
                    text = "Gallery Display & Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Grid columns
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Grid Columns: $gridColumns", fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2, 3, 4, 5).forEach { count ->
                                    FilterChip(
                                        selected = gridColumns == count,
                                        onClick = { scope.launch { preferencesManager.setGridColumns(count) } },
                                        label = { Text("$count cols") }
                                    )
                                }
                            }
                        }

                        // Default Sort
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Default Sorting", fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    SortOrder.DATE_DESC to "Newest",
                                    SortOrder.DATE_ASC to "Oldest",
                                    SortOrder.NAME_ASC to "Name",
                                    SortOrder.SIZE_DESC to "Size"
                                ).forEach { (order, label) ->
                                    FilterChip(
                                        selected = sortOrder == order,
                                        onClick = { scope.launch { preferencesManager.setSortOrder(order) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Theme Mode
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Theme Mode", fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    AppThemeMode.SYSTEM to "System",
                                    AppThemeMode.LIGHT to "Light",
                                    AppThemeMode.DARK to "Dark"
                                ).forEach { (mode, label) ->
                                    FilterChip(
                                        selected = themeMode == mode,
                                        onClick = { scope.launch { preferencesManager.setThemeMode(mode) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Security & App Info
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Privacy & Security Guarantee", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "CloudGallery is a 100% private, local-first application. No tracking, no user accounts, and no third-party telemetry. All R2 traffic goes directly between your device and your Cloudflare account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CloudGallery Native Android • v1.0",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
