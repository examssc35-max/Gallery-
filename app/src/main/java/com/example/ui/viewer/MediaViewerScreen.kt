package com.example.ui.viewer

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeletionResult
import com.example.domain.model.MediaItem
import com.example.domain.repository.BackupRepository
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.R2Repository
import com.example.domain.repository.UploadResult
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    mediaRepository: MediaRepository,
    backupRepository: BackupRepository,
    r2Repository: R2Repository? = null,
    onItemDeleted: ((MediaItem) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No media items to display", color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val safeInitialIndex = initialIndex.coerceIn(0, mediaList.size - 1)
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { mediaList.size }
    )

    val currentItem = mediaList.getOrNull(pagerState.currentPage) ?: mediaList[0]

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlayingVideo by remember { mutableStateOf(false) }
    var isFavorite by remember(currentItem.id) { mutableStateOf(currentItem.isFavorite) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var isUploadingToR2 by remember { mutableStateOf(false) }
    var isDownloadingFromR2 by remember { mutableStateOf(false) }
    var isDownloaded by remember(currentItem.id) { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf<String?>(null) }
    var isAlreadyBackedUp by remember(currentItem.id) { mutableStateOf(false) }

    // Check backup status for current item
    LaunchedEffect(currentItem.id) {
        if (!currentItem.isCloud) {
            isAlreadyBackedUp = backupRepository.isAlreadyBackedUp(currentItem)
        }
        isFavorite = mediaRepository.isFavorite(currentItem.id)
        isPlayingVideo = false
        isDownloaded = false
    }

    // Android MediaStore system delete contract
    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                onItemDeleted?.invoke(currentItem)
                snackbarHostState.showSnackbar("Deleted from device")
                onBack()
            }
        }
    }

    // Full screen video mode
    if (currentItem.isVideo && isPlayingVideo) {
        VideoPlayerView(
            uriString = currentItem.uriString,
            title = currentItem.name,
            onBack = { isPlayingVideo = false }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal Pager for photos / videos (Supports left/right swipe, pinch-to-zoom, double-tap zoom)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = mediaList[page]
            if (item.isVideo) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImageView(
                        uriString = item.uriString,
                        contentDescription = item.name,
                        onTap = { controlsVisible = !controlsVisible }
                    )
                    // Big Play Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(76.dp)
                            .clickable { isPlayingVideo = true }
                            .testTag("viewer_play_video_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            } else {
                ZoomableImageView(
                    uriString = item.uriString,
                    contentDescription = item.name,
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
        }

        // Top App Bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (currentItem.isCloud) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0288D1).copy(alpha = 0.75f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = "Cloud",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Cloudflare R2",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = currentItem.name,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { showDetailsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Bar: Comfortable, safe padding above navigation bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF1E222D).copy(alpha = 0.92f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Cloud Download OR Save to R2 Action
                        if (currentItem.isCloud) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(enabled = !isDownloadingFromR2) {
                                        val key = currentItem.cloudKey ?: currentItem.path
                                        if (r2Repository != null && key.isNotEmpty()) {
                                            isDownloadingFromR2 = true
                                            scope.launch {
                                                try {
                                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                    val targetFile = File(downloadsDir, currentItem.name)
                                                    val result = r2Repository.downloadKeyToFile(key, targetFile)
                                                    isDownloadingFromR2 = false
                                                    if (result.isSuccess) {
                                                        isDownloaded = true
                                                        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                                                        snackbarHostState.showSnackbar("Downloaded to Downloads: ${currentItem.name}")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Download failed: ${result.exceptionOrNull()?.message}")
                                                    }
                                                } catch (e: Exception) {
                                                    isDownloadingFromR2 = false
                                                    snackbarHostState.showSnackbar("Download failed: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("viewer_download_cloud_button")
                            ) {
                                if (isDownloadingFromR2) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isDownloaded) "Downloaded" else "Download",
                                    color = if (isDownloaded) Color(0xFF4CAF50) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(enabled = !isUploadingToR2) {
                                        if (isAlreadyBackedUp) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Already backed up to Cloudflare R2")
                                            }
                                        } else {
                                            isUploadingToR2 = true
                                            uploadStatusText = "Uploading..."
                                            scope.launch {
                                                val result = backupRepository.uploadSingleMedia(currentItem)
                                                isUploadingToR2 = false
                                                when (result) {
                                                    is UploadResult.Success -> {
                                                        isAlreadyBackedUp = true
                                                        snackbarHostState.showSnackbar("Saved to Cloudflare R2!")
                                                    }
                                                    is UploadResult.AlreadyBackedUp -> {
                                                        isAlreadyBackedUp = true
                                                        snackbarHostState.showSnackbar("Already backed up to R2")
                                                    }
                                                    is UploadResult.Failure -> {
                                                        snackbarHostState.showSnackbar("Upload failed: ${result.error}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("viewer_save_to_r2_button")
                            ) {
                                if (isUploadingToR2) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (isAlreadyBackedUp) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Backed up",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Save to R2",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAlreadyBackedUp) "Backed Up" else "Save to R2",
                                    color = if (isAlreadyBackedUp) Color(0xFF4CAF50) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 2. Share Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    shareMedia(context, currentItem)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Share", color = Color.White, fontSize = 11.sp)
                        }

                        // 3. Favorite Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        mediaRepository.toggleFavorite(currentItem.id)
                                        isFavorite = !isFavorite
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("viewer_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFF4081) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Favorite", color = Color.White, fontSize = 11.sp)
                        }

                        // 4. Delete Action (With Confirmation Dialog)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showDeleteConfirmDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("viewer_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Delete", color = Color(0xFFFF5252), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (currentItem.isCloud) "Delete from Cloudflare R2" else "Delete Media") },
            text = {
                Text(
                    if (currentItem.isCloud)
                        "Are you sure you want to permanently delete \"${currentItem.name}\" from your Cloudflare R2 bucket? This action cannot be undone."
                    else
                        "Are you sure you want to delete \"${currentItem.name}\" (${formatFileSize(currentItem.size)}) from your device?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            if (currentItem.isCloud) {
                                val key = currentItem.cloudKey ?: currentItem.path
                                val result = r2Repository?.deleteObject(key)
                                if (result != null && result.isSuccess) {
                                    onItemDeleted?.invoke(currentItem)
                                    snackbarHostState.showSnackbar("Deleted from Cloudflare R2")
                                    onBack()
                                } else {
                                    snackbarHostState.showSnackbar("Delete failed: ${result?.exceptionOrNull()?.message ?: "Unknown error"}")
                                }
                            } else {
                                val result = mediaRepository.deleteMedia(listOf(currentItem))
                                when (result) {
                                    is DeletionResult.Success -> {
                                        onItemDeleted?.invoke(currentItem)
                                        snackbarHostState.showSnackbar("Deleted successfully")
                                        onBack()
                                    }
                                    is DeletionResult.RequiresUserConsent -> {
                                        val request = IntentSenderRequest.Builder(result.intentSender).build()
                                        deleteIntentSenderLauncher.launch(request)
                                    }
                                    is DeletionResult.Failure -> {
                                        snackbarHostState.showSnackbar("Delete failed: ${result.error}")
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Media Details Dialog
    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(label = "File Name", value = currentItem.name)
                    DetailRow(label = "Storage", value = if (currentItem.isCloud) "Cloudflare R2" else "Internal / Device")
                    DetailRow(label = "Size", value = formatFileSize(currentItem.size))
                    DetailRow(label = "Type", value = currentItem.mimeType)
                    if (currentItem.isVideo && currentItem.durationMs > 0) {
                        DetailRow(label = "Duration", value = formatDuration(currentItem.durationMs))
                    }
                    if (currentItem.path.isNotEmpty()) {
                        DetailRow(label = if (currentItem.isCloud) "R2 Key" else "Path", value = currentItem.path)
                    }
                    if (currentItem.dateModified > 0) {
                        DetailRow(label = "Date Modified", value = formatDate(currentItem.dateModified))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(text = value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareMedia(context: Context, item: MediaItem) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            if (item.isCloud && !item.uriString.startsWith("content://") && !item.uriString.startsWith("file://")) {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, item.name)
                putExtra(Intent.EXTRA_TEXT, "Cloud file: ${item.name}\n${item.uriString}")
            } else {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
    } catch (_: Exception) {}
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

private fun formatDuration(millis: Long): String {
    val sec = millis / 1000
    val min = sec / 60
    val remSec = sec % 60
    return String.format(Locale.US, "%d:%02d", min, remSec)
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
