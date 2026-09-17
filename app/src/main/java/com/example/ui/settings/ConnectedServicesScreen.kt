package com.example.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.R2Credentials
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedServicesScreen(
    r2Repository: R2Repository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials by r2Repository.credentialsFlow.collectAsState(initial = R2Credentials())

    val isConnected = credentials.secretAccessKey.isNotEmpty() && credentials.bucketName.isNotEmpty()

    var showConfigModal by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    var accountId by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Synchronize editing state whenever modal opens or credentials change
    LaunchedEffect(credentials, showConfigModal) {
        if (showConfigModal) {
            accountId = credentials.accountId
            accessKeyId = credentials.accessKeyId
            secretAccessKey = credentials.secretAccessKey
            bucketName = credentials.bucketName
            endpoint = credentials.endpoint
            testResult = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cloud Storage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Cloudflare R2",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isConnected) "Connected • ${credentials.bucketName}" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isConnected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isConnected) "Active" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cloudflare R2 is an S3-compatible cloud object storage with zero egress fees. All cloud backups and file operations in CloudGallery run directly through your own R2 bucket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showConfigModal = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("configure_r2_button")
                        ) {
                            Text(if (isConnected) "Reconfigure R2" else "Configure R2")
                        }

                        if (isConnected) {
                            OutlinedButton(
                                onClick = { showDisconnectConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("disconnect_r2_button")
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }

            // Connection Details Card (shown when connected)
            if (isConnected) {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "R2 Bucket Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        InfoRow("Bucket Name", credentials.bucketName)
                        if (credentials.accountId.isNotEmpty()) {
                            InfoRow("Account ID", credentials.accountId)
                        }
                        InfoRow(
                            "Access Key ID",
                            if (credentials.accessKeyId.length > 8)
                                "${credentials.accessKeyId.take(4)}...${credentials.accessKeyId.takeLast(4)}"
                            else credentials.accessKeyId
                        )
                        if (credentials.endpoint.isNotEmpty()) {
                            InfoRow("Endpoint", credentials.endpoint)
                        }
                    }
                }
            }
        }
    }

    // Configure Cloudflare R2 Dialog
    if (showConfigModal) {
        AlertDialog(
            onDismissRequest = { if (!isTestingConnection && !isSaving) showConfigModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configure Cloudflare R2",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter your Cloudflare R2 S3-compatible credentials. You can find these in the Cloudflare Dashboard under R2 > Manage R2 API Tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it },
                        label = { Text("Account ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("r2_account_id_input")
                    )

                    OutlinedTextField(
                        value = accessKeyId,
                        onValueChange = { accessKeyId = it },
                        label = { Text("Access Key ID *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("r2_access_key_input")
                    )

                    OutlinedTextField(
                        value = secretAccessKey,
                        onValueChange = { secretAccessKey = it },
                        label = { Text("Secret Access Key *") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("r2_secret_key_input")
                    )

                    OutlinedTextField(
                        value = bucketName,
                        onValueChange = { bucketName = it },
                        label = { Text("Bucket Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("r2_bucket_name_input")
                    )

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Custom Endpoint (Optional)") },
                        placeholder = { Text("https://<account-id>.r2.cloudflarestorage.com", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("r2_endpoint_input")
                    )

                    // Test Result Feedback Banner
                    testResult?.let { (success, msg) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (success) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Test Connection Button
                    OutlinedButton(
                        onClick = {
                            val tempCreds = R2Credentials(
                                accountId = accountId.trim(),
                                accessKeyId = accessKeyId.trim(),
                                secretAccessKey = secretAccessKey.trim(),
                                bucketName = bucketName.trim(),
                                endpoint = endpoint.trim()
                            )
                            isTestingConnection = true
                            testResult = null
                            scope.launch {
                                val res = r2Repository.testConnection(tempCreds)
                                isTestingConnection = false
                                if (res.isSuccess) {
                                    testResult = true to "Connection successful! Bucket is accessible."
                                } else {
                                    val err = res.exceptionOrNull()?.localizedMessage ?: "Failed to connect"
                                    testResult = false to "Connection failed: $err"
                                }
                            }
                        },
                        enabled = !isTestingConnection && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucketName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("test_r2_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Connection...")
                        } else {
                            Text("Test Connection")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCreds = R2Credentials(
                            accountId = accountId.trim(),
                            accessKeyId = accessKeyId.trim(),
                            secretAccessKey = secretAccessKey.trim(),
                            bucketName = bucketName.trim(),
                            endpoint = endpoint.trim(),
                            isVerified = testResult?.first == true || credentials.isVerified
                        )
                        isSaving = true
                        scope.launch {
                            r2Repository.saveCredentials(newCreds)
                            isSaving = false
                            showConfigModal = false
                            Toast.makeText(context, "Cloudflare R2 settings saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSaving && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && bucketName.isNotBlank(),
                    modifier = Modifier.testTag("save_r2_credentials_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfigModal = false },
                    enabled = !isTestingConnection && !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Cloudflare R2") },
            text = { Text("Are you sure you want to disconnect Cloudflare R2? Your credentials will be cleared locally. No files in your bucket will be modified or deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            r2Repository.disconnect()
                            showDisconnectConfirm = false
                            Toast.makeText(context, "Cloudflare R2 disconnected", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
