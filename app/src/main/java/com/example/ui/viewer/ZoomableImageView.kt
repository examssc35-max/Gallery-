package com.example.ui.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlin.math.abs

@Composable
fun ZoomableImageView(
    uriString: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    resetZoomTrigger: Int = 0,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onTap: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom and pan when swiped away to another page or when reset is triggered
    LaunchedEffect(isCurrentPage, resetZoomTrigger) {
        scale = 1f
        offset = Offset.Zero
        if (isCurrentPage) {
            onZoomChanged?.invoke(false)
        }
    }

    LaunchedEffect(scale, isCurrentPage) {
        if (isCurrentPage) {
            onZoomChanged?.invoke(scale > 1.05f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var lastPressedCount = 1
                    var isDragging = false
                    var panAccumulator = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount == 0) break

                        // Skip calculations if pointer count changed on this event to avoid jumps
                        if (pressedCount != lastPressedCount) {
                            lastPressedCount = pressedCount
                            panAccumulator = Offset.Zero
                            continue
                        }

                        if (pressedCount >= 2) {
                            // Pinch-to-zoom & multi-finger pan
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale

                            if (scale > 1f) {
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                val newOffset = offset + panChange
                                offset = Offset(
                                    x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }

                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        } else if (pressedCount == 1) {
                            if (scale > 1.01f) {
                                // Zoomed in: single finger pan
                                val panChange = event.calculatePan()
                                panAccumulator += panChange

                                val touchSlop = viewConfiguration.touchSlop
                                if (!isDragging && panAccumulator.getDistance() > touchSlop) {
                                    isDragging = true
                                }

                                if (isDragging) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2f

                                    val canPanX = when {
                                        panChange.x < -0.5f -> offset.x > -maxOffsetX + 1f
                                        panChange.x > 0.5f -> offset.x < maxOffsetX - 1f
                                        else -> false
                                    }

                                    val canPanY = when {
                                        panChange.y < -0.5f -> offset.y > -maxOffsetY + 1f
                                        panChange.y > 0.5f -> offset.y < maxOffsetY - 1f
                                        else -> false
                                    }

                                    if (abs(panChange.x) > abs(panChange.y)) {
                                        // Horizontal swipe gesture
                                        if (canPanX) {
                                            val newX = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            val newY = if (canPanY) (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY) else offset.y
                                            offset = Offset(newX, newY)
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                        // If at horizontal boundary and user swipes outward (!canPanX),
                                        // do NOT consume the horizontal drag.
                                        // HorizontalPager receives the gesture to navigate to next/prev image!
                                    } else {
                                        // Vertical pan gesture
                                        if (canPanY) {
                                            val newY = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            offset = Offset(offset.x, newY)
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                }
                            } else {
                                // scale <= 1.01f: Not zoomed.
                                // Do NOT consume pointer events!
                                // HorizontalPager seamlessly handles swipe left -> next, swipe right -> previous!
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uriString)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Failed to load image",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        )
    }
}

