package com.example.ui.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MediaCategory
import com.example.domain.model.StorageUsage
import com.example.util.StorageFormatter
import java.text.NumberFormat
import java.util.Locale

// Semantic Colors for categories
val PhotoColor = Color(0xFF1976D2)   // Vibrant Blue
val VideoColor = Color(0xFF7B1FA2)   // Deep Purple
val OtherColor = Color(0xFFF57C00)   // Vibrant Amber / Orange

@Composable
fun StorageSegmentedBar(
    usage: StorageUsage,
    modifier: Modifier = Modifier
) {
    val photoFraction by animateFloatAsState(
        targetValue = usage.photoPercentage,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "photoFraction"
    )
    val videoFraction by animateFloatAsState(
        targetValue = usage.videoPercentage,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "videoFraction"
    )
    val otherFraction by animateFloatAsState(
        targetValue = usage.otherPercentage,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "otherFraction"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Segmented Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .testTag("storage_segmented_bar")
        ) {
            if (usage.totalBytes > 0L) {
                Row(modifier = Modifier.matchParentSize()) {
                    if (photoFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(photoFraction.coerceAtLeast(0.001f))
                                .height(14.dp)
                                .background(PhotoColor)
                        )
                    }
                    if (videoFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(videoFraction.coerceAtLeast(0.001f))
                                .height(14.dp)
                                .background(VideoColor)
                        )
                    }
                    if (otherFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(otherFraction.coerceAtLeast(0.001f))
                                .height(14.dp)
                                .background(OtherColor)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendPill(
                color = PhotoColor,
                label = "Photos",
                sizeText = StorageFormatter.formatStorageSize(usage.photoBytes)
            )
            LegendPill(
                color = VideoColor,
                label = "Videos",
                sizeText = StorageFormatter.formatStorageSize(usage.videoBytes)
            )
            LegendPill(
                color = OtherColor,
                label = "Other",
                sizeText = StorageFormatter.formatStorageSize(usage.otherBytes)
            )
        }
    }
}

@Composable
private fun LegendPill(
    color: Color,
    label: String,
    sizeText: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StorageCategoryItem(
    category: MediaCategory,
    title: String,
    count: Int,
    bytes: Long,
    totalBytes: Long,
    icon: ImageVector,
    accentColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val percentage = if (totalBytes > 0L) (bytes.toFloat() / totalBytes * 100f).toInt() else 0
    val formattedCount = NumberFormat.getNumberInstance(Locale.US).format(count)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with tinted circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title and File Count
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (totalBytes > 0L && bytes > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedCount files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Size text & optional forward arrow
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = StorageFormatter.formatStorageSize(bytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View in Cloud",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
