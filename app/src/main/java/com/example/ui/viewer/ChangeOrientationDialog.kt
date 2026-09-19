package com.example.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.MediaItem
import com.example.domain.repository.R2Repository
import com.example.util.ImageOrientationInfo
import com.example.util.ImageOrientationProcessor
import com.example.util.TargetOrientation
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * ChangeOrientationDialog
 *
 * Provides a dedicated, interactive preview and canvas generator:
 * - Shows current orientation clearly (Portrait vs Landscape)
 * - Allows toggling target orientation
 * - Displays a live visual canvas preview (Original -> New Canvas with smart blurred fill)
 * - Displays progress and error handling with Retry / Cancel
 * - Calls ImageOrientationProcessor on Apply to create the physical new image
 */
@Composable
fun ChangeOrientationDialog(
    item: MediaItem,
    r2Repository: R2Repository? = null,
    onDismiss: () -> Unit,
    onSuccess: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var orientationInfo by remember { mutableStateOf<ImageOrientationInfo?>(null) }
    var isLoadingInfo by remember { mutableStateOf(true) }

    // Detected or default initial state
    val initialCurrentOrientation = if (item.height >= item.width && item.width > 0) {
        TargetOrientation.PORTRAIT
    } else {
        TargetOrientation.LANDSCAPE
    }

    var selectedOrientation by remember {
        mutableStateOf(
            if (initialCurrentOrientation == TargetOrientation.PORTRAIT) {
                TargetOrientation.LANDSCAPE
            } else {
                TargetOrientation.PORTRAIT
            }
        )
    }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgressText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load detailed image dimensions & EXIF orientation
    LaunchedEffect(item.id) {
        isLoadingInfo = true
        val info = ImageOrientationProcessor.detectImageInfo(context, item)
        orientationInfo = info
        // Automatically default target to opposite orientation
        selectedOrientation = if (info.currentOrientation == TargetOrientation.PORTRAIT) {
            TargetOrientation.LANDSCAPE
        } else {
            TargetOrientation.PORTRAIT
        }
        isLoadingInfo = false
    }

    val currentOrientation = orientationInfo?.currentOrientation ?: initialCurrentOrientation
    val srcW = orientationInfo?.width ?: max(1, item.width)
    val srcH = orientationInfo?.height ?: max(1, item.height)

    val (targetW, targetH) = remember(srcW, srcH, selectedOrientation) {
        ImageOrientationProcessor.computeTargetDimensions(srcW, srcH, selectedOrientation)
    }

    fun applyChange() {
        isProcessing = true
        errorMessage = null
        val orientationLabel = if (selectedOrientation == TargetOrientation.LANDSCAPE) "landscape" else "portrait"
        processingProgressText = "Creating $orientationLabel image…"

        scope.launch {
            val result = ImageOrientationProcessor.processAndSaveOrientation(
                context = context,
                item = item,
                targetOrientation = selectedOrientation,
                r2Repository = r2Repository,
                onProgress = { text ->
                    processingProgressText = text
                }
            )

            isProcessing = false
            if (result.isSuccess) {
                val newMediaItem = result.getOrThrow()
                onSuccess(newMediaItem)
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage
                    ?: "Couldn't change image orientation."
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Change Orientation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transform canvas without cropping the subject",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isProcessing) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Orientation Option Selectors (Portrait vs Landscape)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Target Canvas Orientation",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Portrait Option
                        val isPortraitSelected = selectedOrientation == TargetOrientation.PORTRAIT
                        val isPortraitActive = currentOrientation == TargetOrientation.PORTRAIT
                        OrientationOptionCard(
                            label = "Portrait",
                            icon = Icons.Default.CropPortrait,
                            isActive = isPortraitActive,
                            isSelected = isPortraitSelected,
                            dimensionsText = if (isPortraitActive) "${srcW} × ${srcH}" else "${targetW} × ${targetH}",
                            enabled = !isProcessing,
                            onClick = { selectedOrientation = TargetOrientation.PORTRAIT },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("orientation_option_portrait")
                        )

                        // Landscape Option
                        val isLandscapeSelected = selectedOrientation == TargetOrientation.LANDSCAPE
                        val isLandscapeActive = currentOrientation == TargetOrientation.LANDSCAPE
                        OrientationOptionCard(
                            label = "Landscape",
                            icon = Icons.Default.CropLandscape,
                            isActive = isLandscapeActive,
                            isSelected = isLandscapeSelected,
                            dimensionsText = if (isLandscapeActive) "${srcW} × ${srcH}" else "${targetW} × ${targetH}",
                            enabled = !isProcessing,
                            onClick = { selectedOrientation = TargetOrientation.LANDSCAPE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("orientation_option_landscape")
                        )
                    }
                }

                // 2. Visual Comparison Preview: Original -> Target Canvas
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Visual Canvas Preview",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Smart Blurred Fill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Side by side preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Original
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Original (${srcW}×${srcH})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(if (currentOrientation == TargetOrientation.PORTRAIT) 9f / 16f else 16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(item.uriString)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Original Photo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Arrow Indicator
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "transforms to",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )

                            // Right: New Canvas with Blurred Extension
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "New Canvas (${targetW}×${targetH})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(if (selectedOrientation == TargetOrientation.PORTRAIT) 9f / 16f else 16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 1. Blurred background simulation
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(item.uriString)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(14.dp)
                                    )

                                    // Subtle dark overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f))
                                    )

                                    // 2. Proportionally centered uncropped photo
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(item.uriString)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Proportional Subject",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Main photo content is preserved 100% proportionally without cropping, distortion, or squashed faces.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // 3. Progress State
                AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = processingProgressText.ifEmpty { "Processing canvas…" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 4. Error State with Retry
                AnimatedVisibility(
                    visible = errorMessage != null && !isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Couldn't change image orientation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (!errorMessage.isNullOrBlank()) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (errorMessage != null && !isProcessing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_orientation_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { applyChange() },
                        modifier = Modifier.testTag("retry_orientation_button")
                    ) {
                        Text("Retry")
                    }
                }
            } else {
                Button(
                    onClick = { applyChange() },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("apply_change_orientation_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isProcessing) "Applying…" else "Apply",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (errorMessage == null) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("cancel_orientation_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun OrientationOptionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isSelected: Boolean,
    dimensionsText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = if (enabled) onClick else null,
                    modifier = Modifier.size(20.dp)
                )

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "CURRENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = dimensionsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
