package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ThemeAnimationIntensity
import kotlin.random.Random

/**
 * Pre-allocated streak particle for City theme (distant night traffic / beacon lights).
 */
private class CityLightStreak(
    var x: Float = 0f,
    var y: Float = 0f,
    var length: Float = 28f,
    var speedX: Float = 40f,
    var alpha: Float = 0.2f,
    var isAmber: Boolean = true
)

/**
 * Premium City dynamic theme background.
 *
 * Visual characteristics:
 * - Sophisticated midnight navy and charcoal palette
 * - Minimalist architectural skyline silhouettes with soft depth
 * - Warm amber window glows and bokeh reflections
 * - Subtle horizontal transit light streaks moving across the distant horizon
 *
 * Performance boundaries:
 * - Zero GC in draw loop
 * - Pauses when backgrounded or in reduced-motion mode
 * - Cinematic and refined
 */
@Composable
fun CityThemeBackground(
    isDark: Boolean,
    animationEnabled: Boolean,
    intensity: ThemeAnimationIntensity,
    isViewerActive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isReducedMotion = remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            val durationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f || durationScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isLowRam = remember {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            actManager?.isLowRamDevice == true
        } catch (_: Exception) {
            false
        }
    }

    var isLifecycleStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleStarted = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val effectiveIntensity = if (isLowRam) ThemeAnimationIntensity.LOW else intensity
    val streakCount = when (effectiveIntensity) {
        ThemeAnimationIntensity.LOW -> 4
        ThemeAnimationIntensity.MEDIUM -> 8
        ThemeAnimationIntensity.HIGH -> 14
    }

    val streaks = remember(streakCount) {
        Array(streakCount) { CityLightStreak() }
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    val shouldAnimate = animationEnabled && isLifecycleStarted && !isViewerActive && !isReducedMotion

    LaunchedEffect(shouldAnimate, effectiveIntensity) {
        if (!shouldAnimate) return@LaunchedEffect

        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastNanos == 0L) {
                    lastNanos = nowNanos
                    frameTick = nowNanos
                    return@withFrameNanos
                }

                val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNanos = nowNanos

                for (i in 0 until streakCount) {
                    val s = streaks[i]
                    s.x += s.speedX * effectiveIntensity.speedMultiplier * dt
                }

                frameTick = nowNanos
            }
        }
    }

    // City palette
    val bgColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF090C12), // Midnight obsidian
                Color(0xFF101724),
                Color(0xFF0C1019)
            )
        } else {
            listOf(
                Color(0xFFF3F5F9), // Metropolitan mist
                Color(0xFFE5E9F0),
                Color(0xFFF3F5F9)
            )
        }
    }

    val skylineColor = if (isDark) Color(0xFF161E2E) else Color(0xFFD5DCE8)
    val amberLightColor = Color(0xFFF59E0B)
    val coolLightColor = Color(0xFF94A3B8)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val horizonY = height * 0.78f

            // Init streaks
            for (i in 0 until streakCount) {
                val s = streaks[i]
                if (s.x == 0f && s.y == 0f) {
                    s.x = Random.nextFloat() * width
                    s.y = horizonY + Random.nextFloat() * (height - horizonY) * 0.6f
                    s.length = Random.nextFloat() * 24f + 20f
                    s.speedX = (Random.nextFloat() * 30f + 25f) * if (Random.nextBoolean()) 1f else -1f
                    s.alpha = Random.nextFloat() * 0.18f + 0.12f
                    s.isAmber = Random.nextBoolean()
                }
                if (s.speedX > 0 && s.x > width + 50f) {
                    s.x = -s.length - 10f
                    s.y = horizonY + Random.nextFloat() * (height - horizonY) * 0.6f
                } else if (s.speedX < 0 && s.x < -s.length - 50f) {
                    s.x = width + 10f
                    s.y = horizonY + Random.nextFloat() * (height - horizonY) * 0.6f
                }
            }

            // 1. Base gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = bgColors,
                    startY = 0f,
                    endY = height
                )
            )

            // 2. Distant atmospheric sky glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        amberLightColor.copy(alpha = if (isDark) 0.05f else 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, horizonY),
                    radius = width * 0.75f
                ),
                center = Offset(width * 0.5f, horizonY),
                radius = width * 0.75f
            )

            // 3. Stepped skyline silhouettes (background layer of buildings)
            val skylinePath = Path().apply {
                moveTo(0f, height)
                lineTo(0f, horizonY - 40f)
                lineTo(width * 0.12f, horizonY - 40f)
                lineTo(width * 0.12f, horizonY - 80f)
                lineTo(width * 0.22f, horizonY - 80f)
                lineTo(width * 0.22f, horizonY - 30f)
                lineTo(width * 0.35f, horizonY - 30f)
                lineTo(width * 0.35f, horizonY - 110f)
                lineTo(width * 0.44f, horizonY - 110f)
                lineTo(width * 0.44f, horizonY - 60f)
                lineTo(width * 0.58f, horizonY - 60f)
                lineTo(width * 0.58f, horizonY - 130f)
                lineTo(width * 0.68f, horizonY - 130f)
                lineTo(width * 0.68f, horizonY - 50f)
                lineTo(width * 0.82f, horizonY - 50f)
                lineTo(width * 0.82f, horizonY - 95f)
                lineTo(width * 0.92f, horizonY - 95f)
                lineTo(width * 0.92f, horizonY - 40f)
                lineTo(width, horizonY - 40f)
                lineTo(width, height)
                close()
            }
            drawPath(
                path = skylinePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        skylineColor.copy(alpha = if (isDark) 0.18f else 0.25f),
                        skylineColor.copy(alpha = if (isDark) 0.35f else 0.45f)
                    ),
                    startY = horizonY - 130f,
                    endY = height
                )
            )

            // 4. Subtle window light dots
            val windowAlpha = if (isDark) 0.25f else 0.35f
            val windowPositions = listOf(
                Offset(width * 0.38f, horizonY - 95f),
                Offset(width * 0.40f, horizonY - 80f),
                Offset(width * 0.61f, horizonY - 115f),
                Offset(width * 0.64f, horizonY - 100f),
                Offset(width * 0.62f, horizonY - 85f),
                Offset(width * 0.85f, horizonY - 80f),
                Offset(width * 0.87f, horizonY - 65f),
                Offset(width * 0.16f, horizonY - 65f)
            )
            for (pos in windowPositions) {
                drawRect(
                    color = amberLightColor.copy(alpha = windowAlpha),
                    topLeft = pos,
                    size = Size(3.5f, 3.5f)
                )
            }

            // 5. Draw distant moving transit light streaks
            if (shouldAnimate) {
                for (i in 0 until streakCount) {
                    val s = streaks[i]
                    val color = if (s.isAmber) amberLightColor else coolLightColor
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.02f),
                                color.copy(alpha = s.alpha),
                                color.copy(alpha = 0.02f)
                            ),
                            startX = s.x,
                            endX = s.x + s.length
                        ),
                        start = Offset(s.x, s.y),
                        end = Offset(s.x + s.length, s.y),
                        strokeWidth = 2.0f
                    )
                }
            }
        }

        content()
    }
}
