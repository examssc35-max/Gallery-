package com.example.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOptionsBottomSheet(
    item: MediaItem,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onDownloadOrUpload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onAddToAlbum: (() -> Unit)? = null,
    onChangeOrientation: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onShowDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header: Thumbnail + Name + Size/Date + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(item.size)} • ${formatDate(item.dateAdded)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Actions list
            if (onOpen != null) {
                FileActionRow(
                    icon = Icons.Default.OpenInNew,
                    title = "Open",
                    onClick = {
                        onDismiss()
                        onOpen()
                    }
                )
            }

            if (onDownloadOrUpload != null) {
                FileActionRow(
                    icon = if (item.isCloud) Icons.Default.CloudDownload else Icons.Default.CloudUpload,
                    title = if (item.isCloud) "Download to Device" else "Upload to Cloudflare R2",
                    onClick = {
                        onDismiss()
                        onDownloadOrUpload()
                    }
                )
            }

            FileActionRow(
                icon = Icons.Default.Share,
                title = "Share",
                onClick = {
                    onDismiss()
                    if (onShare != null) {
                        onShare()
                    } else {
                        shareMediaItem(context, item)
                    }
                }
            )

            if (onToggleFavorite != null) {
                FileActionRow(
                    icon = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (item.isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                    title = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    onClick = {
                        onDismiss()
                        onToggleFavorite()
                    }
                )
            }

            if (onAddToAlbum != null) {
                FileActionRow(
                    icon = Icons.Default.PhotoAlbum,
                    title = "Add to Album",
                    onClick = {
                        onDismiss()
                        onAddToAlbum()
                    }
                )
            }

            if (!item.isVideo && onChangeOrientation != null) {
                FileActionRow(
                    painter = painterResource(com.example.R.drawable.ic_change_orientation),
                    title = "Change Orientation",
                    modifier = Modifier.testTag("change_orientation_menu_item"),
                    onClick = {
                        onDismiss()
                        onChangeOrientation()
                    }
                )
            }

            if (onRename != null) {
                FileActionRow(
                    icon = Icons.Default.DriveFileRenameOutline,
                    title = "Rename",
                    onClick = {
                        onDismiss()
                        onRename()
                    }
                )
            }

            if (onShowDetails != null) {
                FileActionRow(
                    icon = Icons.Default.Info,
                    title = "File Info",
                    onClick = {
                        onDismiss()
                        onShowDetails()
                    }
                )
            }

            if (onDelete != null) {
                FileActionRow(
                    icon = Icons.Default.DeleteOutline,
                    title = "Delete",
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        onDelete()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FileActionRow(
    icon: ImageVector? = null,
    painter: Painter? = null,
    title: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val effectiveIconTint = if (isDestructive) MaterialTheme.colorScheme.error else iconTint

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = effectiveIconTint,
                modifier = Modifier.size(22.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveIconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isDestructive) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0) return ""
    val date = Date(timestampSeconds * 1000)
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(date)
}

private fun shareMediaItem(context: Context, item: MediaItem) {
    try {
        val uri = Uri.parse(item.uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
    } catch (_: Exception) {}
}
