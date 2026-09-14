package com.example.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai.model.AiActionResult
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import java.util.Locale

@Composable
fun AiActionCard(
    actionResult: AiActionResult,
    onMediaClick: (initialIndex: Int, items: List<MediaItem>) -> Unit = { _, _ -> },
    onNavigateToRoute: (String) -> Unit = {},
    onRunAction: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("ai_action_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            when (actionResult) {
                is AiActionResult.MediaListResult -> {
                    MediaListCardContent(actionResult, onMediaClick)
                }
                is AiActionResult.CloudListResult -> {
                    CloudListCardContent(actionResult, onNavigateToRoute)
                }
                is AiActionResult.StorageUsageResult -> {
                    StorageUsageCardContent(actionResult, onNavigateToRoute)
                }
                is AiActionResult.BackupStatusResult -> {
                    BackupStatusCardContent(actionResult, onNavigateToRoute, onRunAction)
                }
                is AiActionResult.R2ConnectionResult -> {
                    R2ConnectionCardContent(actionResult, onNavigateToRoute, onRunAction)
                }
                is AiActionResult.DuplicatesResult -> {
                    DuplicatesCardContent(actionResult, onNavigateToRoute)
                }
                is AiActionResult.FailureDiagnosticResult -> {
                    DiagnosticCardContent(actionResult, onNavigateToRoute)
                }
                is AiActionResult.SimpleActionResult -> {
                    SimpleActionCardContent(actionResult, onNavigateToRoute)
                }
            }
        }
    }
}

@Composable
private fun MediaListCardContent(
    result: AiActionResult.MediaListResult,
    onMediaClick: (initialIndex: Int, items: List<MediaItem>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "${result.totalCount}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    if (result.filterDescription.isNotBlank()) {
        Text(
            text = result.filterDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
    }

    if (result.items.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(result.items, key = { _, item -> item.id }) { index, item ->
                MediaThumbnailChip(item = item, onClick = { onMediaClick(index, result.items) })
            }
        }
    }
}

@Composable
private fun MediaThumbnailChip(
    item: MediaItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("media_chip_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )

                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBytes(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CloudListCardContent(
    result: AiActionResult.CloudListResult,
    onNavigateToRoute: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "${result.totalCount} items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    result.items.take(4).forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatBytes(item.size),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
        onClick = { onNavigateToRoute("cloud") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Open Cloud Browser")
    }
}

@Composable
private fun StorageUsageCardContent(
    result: AiActionResult.StorageUsageResult,
    onNavigateToRoute: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Storage Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    result.storageUsage?.let { r2 ->
        Text(
            text = "Cloudflare R2 Bucket: ${formatBytes(r2.totalBytes)} (${r2.totalObjectCount} objects)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))

        val total = if (r2.totalBytes > 0) r2.totalBytes.toFloat() else 1f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (r2.photoBytes > 0) {
                Box(
                    modifier = Modifier
                        .weight(r2.photoBytes.toFloat() / total)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xFF2563EB))
                )
            }
            if (r2.videoBytes > 0) {
                Box(
                    modifier = Modifier
                        .weight(r2.videoBytes.toFloat() / total)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xFFD97706))
                )
            }
            if (r2.otherBytes > 0) {
                Box(
                    modifier = Modifier
                        .weight(r2.otherBytes.toFloat() / total)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color(0xFF7C3AED))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Photos: ${formatBytes(r2.photoBytes)}", style = MaterialTheme.typography.labelSmall)
            Text("Videos: ${formatBytes(r2.videoBytes)}", style = MaterialTheme.typography.labelSmall)
            Text("Other: ${formatBytes(r2.otherBytes)}", style = MaterialTheme.typography.labelSmall)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onNavigateToRoute("cloud_storage_usage") },
            modifier = Modifier.weight(1f)
        ) {
            Text("Cloud Usage")
        }
        OutlinedButton(
            onClick = { onNavigateToRoute("storage") },
            modifier = Modifier.weight(1f)
        ) {
            Text("Device Analyzer")
        }
    }
}

@Composable
private fun BackupStatusCardContent(
    result: AiActionResult.BackupStatusResult,
    onNavigateToRoute: (String) -> Unit,
    onRunAction: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Backup & Sync Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(
            shape = CircleShape,
            color = if (result.isAutoBackupEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = if (result.isAutoBackupEnabled) "Auto-Backup ON" else "Auto-Backup OFF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (result.isAutoBackupEnabled) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatusCounter("Backed Up", result.backedUpCount.toString(), Color(0xFF10B981))
        StatusCounter("Pending", result.pendingCount.toString(), MaterialTheme.colorScheme.primary)
        StatusCounter("Failed", result.failedCount.toString(), MaterialTheme.colorScheme.error)
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Last Backup: ${result.lastBackupTimeFormatted}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onRunAction("Back up my videos") },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run Backup")
        }
        OutlinedButton(
            onClick = { onNavigateToRoute("backup") },
            modifier = Modifier.weight(1f)
        ) {
            Text("Backup Details")
        }
    }
}

@Composable
private fun StatusCounter(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun R2ConnectionCardContent(
    result: AiActionResult.R2ConnectionResult,
    onNavigateToRoute: (String) -> Unit,
    onRunAction: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (result.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (result.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (result.isConnected) "R2 Connected" else "R2 Not Connected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = result.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onRunAction("Check my R2 connection") },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retest")
        }
        Button(
            onClick = { onNavigateToRoute("settings") },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Settings")
        }
    }
}

@Composable
private fun DuplicatesCardContent(
    result: AiActionResult.DuplicatesResult,
    onNavigateToRoute: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Duplicate Files Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "${result.duplicateItemsCount} duplicate files in ${result.groupCount} groups, occupying ${result.formattedWastedSize} of storage space.",
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = { onNavigateToRoute("duplicates") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open Duplicate Cleaner")
    }
}

@Composable
private fun DiagnosticCardContent(
    result: AiActionResult.FailureDiagnosticResult,
    onNavigateToRoute: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFD97706),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Potential issues identified:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )

    result.causes.forEach { cause ->
        Text(
            text = "• $cause",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Recommended steps:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )

    result.recommendedFixes.forEach { fix ->
        Text(
            text = "→ $fix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = { onNavigateToRoute("settings") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Go to Settings")
    }
}

@Composable
private fun SimpleActionCardContent(
    result: AiActionResult.SimpleActionResult,
    onNavigateToRoute: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (result.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = result.message,
        style = MaterialTheme.typography.bodyMedium
    )

    if (result.navigateRoute != null) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onNavigateToRoute(result.navigateRoute) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Details")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}
