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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.WaterAnimationIntensity
import com.example.data.local.WaterColor
import com.example.data.local.WaterDropSettings
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mutable state representing a single falling water droplet.
 * Pre-allocated to avoid object creation in the animation loop.
 */
private class DropletParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var speed: Float = 130f,
    var length: Float = 14f,
    var width: Float = 2.4f,
    var alpha: Float = 0.25f,
    var targetSurfaceY: Float = 0f,
    var nextSpawnNanos: Long = 0L,
    var isFalling: Boolean = false
)

/**
 * Mutable state representing an expanding water ripple.
 * Pre-allocated to avoid object creation in the animation loop.
 */
private class RippleParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var currentRadius: Float = 0f,
    var maxRadius: Float = 45f,
    var speed: Float = 42f,
    var alpha: Float = 0f,
    var active: Boolean = false
)

/**
 * Premium Water Drop dynamic background.
 *
 * Implements a subtle, performant background featuring:
 * - Fluid translucent glass gradients
 * - Soft caustics / specular water reflections
 * - Gentle falling water droplets
 * - Expanding ripples upon surface contact
 *
 * Designed with strict performance boundaries:
 * - Zero GC allocation in the draw loop
 * - Pauses automatically when backgrounded, in reduced-motion mode, or in viewer mode
 * - Single Canvas background layer that does not trigger recomposition of children
 */
@Composable
fun WaterDropBackground(
    settings: WaterDropSettings,
    isDark: Boolean,
    isViewerActive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check system accessibility settings for reduced motion
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

    // Check if device is low RAM to automatically reduce animation complexity
    val isLowRam = remember {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            actManager?.isLowRamDevice == true
        } catch (_: Exception) {
            false
        }
    }

    // Observe lifecycle state to pause animation when app is paused/stopped
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

    // Effective intensity, capped if low-RAM device
    val effectiveIntensity = if (isLowRam) WaterAnimationIntensity.LOW else settings.intensity
    val maxDroplets = effectiveIntensity.dropCount
    val maxRipples = maxDroplets + 4

    // Fixed particle pools (no GC allocations during render loop)
    val droplets = remember(maxDroplets) {
        Array(maxDroplets) { DropletParticle() }
    }
    val ripples = remember(maxRipples) {
        Array(maxRipples) { RippleParticle() }
    }

    // Animation frame tick state: triggers Canvas redraw only
    var frameTick by remember { mutableLongStateOf(0L) }

    // Should the animation tick run?
    val shouldAnimate = settings.animationEnabled && isLifecycleStarted && !isViewerActive && !isReducedMotion

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

                // Update droplets
                for (i in 0 until maxDroplets) {
                    val drop = droplets[i]
                    if (!drop.isFalling) {
                        if (nowNanos >= drop.nextSpawnNanos) {
                            drop.isFalling = true
                        }
                    } else {
                        drop.y += drop.speed * dt
                        if (drop.y >= drop.targetSurfaceY && drop.targetSurfaceY > 0f) {
                            // Reached surface
                            drop.isFalling = false
                            drop.y = -Random.nextFloat() * 80f - 20f
                            val delayNanos = (Random.nextLong(600_000_000L, 2_400_000_000L) / effectiveIntensity.speedMultiplier).toLong()
                            drop.nextSpawnNanos = nowNanos + delayNanos

                            // Spawn ripple if enabled
                            if (settings.rippleEnabled) {
                                val freeRipple = ripples.firstOrNull { !it.active }
                                    ?: ripples.maxByOrNull { it.currentRadius / it.maxRadius }
                                freeRipple?.let { rip ->
                                    rip.x = drop.x
                                    rip.y = drop.targetSurfaceY
                                    rip.currentRadius = 2.5f
                                    rip.maxRadius = Random.nextFloat() * 22f + 32f
                                    rip.speed = (Random.nextFloat() * 15f + 38f) * effectiveIntensity.speedMultiplier
                                    rip.alpha = Random.nextFloat() * 0.12f + 0.22f
                                    rip.active = true
                                }
                            }
                        }
                    }
                }

                // Update ripples
                if (settings.rippleEnabled) {
                    for (i in 0 until maxRipples) {
                        val rip = ripples[i]
                        if (rip.active) {
                            rip.currentRadius += rip.speed * dt
                            val progress = (rip.currentRadius / rip.maxRadius).coerceIn(0f, 1f)
                            rip.alpha = (1f - progress) * 0.28f
                            if (progress >= 1f) {
                                rip.active = false
                            }
                        }
                    }
                }

                frameTick = nowNanos
            }
        }
    }

    // Palette resolution
    val waterColor = settings.waterColor
    val primaryColor = Color(waterColor.primaryHex)
    val secondaryColor = Color(waterColor.secondaryHex)
    val dropletColor = Color(waterColor.dropletHex)

    // Base background gradient colors
    val baseGradientColors = remember(isDark, waterColor) {
        if (isDark) {
            listOf(
                Color(waterColor.surfaceHex),
                Color(0xFF071420),
                Color(waterColor.surfaceHex)
            )
        } else {
            listOf(
                Color(0xFFF0F9FF),
                Color(0xFFE0F2FE),
                Color(0xFFF0F9FF)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tick to trigger redraw during animation
            val currentNanos = if (shouldAnimate) frameTick else 0L

            val width = size.width
            val height = size.height

            if (width <= 0f || height <= 0f) return@Canvas

            // Initialize uninitialized particles
            for (i in 0 until maxDroplets) {
                val drop = droplets[i]
                if (drop.x == 0f) {
                    drop.x = Random.nextFloat() * (width * 0.88f) + (width * 0.06f)
                    drop.y = -Random.nextFloat() * 120f - 20f
                    drop.targetSurfaceY = Random.nextFloat() * (height * 0.52f) + (height * 0.42f)
                    drop.speed = (Random.nextFloat() * 35f + 115f) * effectiveIntensity.speedMultiplier
                    drop.length = Random.nextFloat() * 6f + 11f
                    drop.width = Random.nextFloat() * 0.8f + 2.0f
                    drop.alpha = Random.nextFloat() * 0.12f + 0.22f
                    drop.nextSpawnNanos = currentNanos + Random.nextLong(100_000_000L, 1_600_000_000L)
                    drop.isFalling = false
                }
            }

            // 1. Draw base fluid gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = baseGradientColors,
                    startY = 0f,
                    endY = height
                )
            )

            // 2. Soft caustics / specular reflections (if blur enabled)
            if (settings.blurEnabled) {
                val timeSec = if (currentNanos > 0L) (currentNanos / 1_000_000_000.0) % 60.0 else 0.0
                val caustic1X = width * (0.35f + 0.12f * sin(timeSec * 0.45).toFloat())
                val caustic1Y = height * (0.28f + 0.07f * sin(timeSec * 0.35).toFloat())

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isDark) 0.09f else 0.14f),
                            primaryColor.copy(alpha = if (isDark) 0.03f else 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(caustic1X, caustic1Y),
                        radius = width * 0.70f
                    ),
                    center = Offset(caustic1X, caustic1Y),
                    radius = width * 0.70f
                )

                val caustic2X = width * (0.68f - 0.10f * sin(timeSec * 0.38).toFloat())
                val caustic2Y = height * (0.76f - 0.06f * sin(timeSec * 0.28).toFloat())

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = if (isDark) 0.07f else 0.11f),
                            secondaryColor.copy(alpha = if (isDark) 0.02f else 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(caustic2X, caustic2Y),
                        radius = width * 0.80f
                    ),
                    center = Offset(caustic2X, caustic2Y),
                    radius = width * 0.80f
                )
            }

            // 3. Draw active ripples
            if (settings.rippleEnabled && shouldAnimate) {
                for (i in 0 until maxRipples) {
                    val rip = ripples[i]
                    if (rip.active && rip.alpha > 0.01f) {
                        val progress = (rip.currentRadius / rip.maxRadius).coerceIn(0f, 1f)
                        val strokeW = (2.2f * (1f - progress) + 0.6f)

                        // Outer wave ring
                        drawCircle(
                            color = primaryColor.copy(alpha = rip.alpha),
                            center = Offset(rip.x, rip.y),
                            radius = rip.currentRadius,
                            style = Stroke(width = strokeW)
                        )

                        // Inner echo wave ring
                        if (rip.currentRadius > 10f) {
                            val innerR = (rip.currentRadius - 9f).coerceAtLeast(0f)
                            drawCircle(
                                color = secondaryColor.copy(alpha = rip.alpha * 0.45f),
                                center = Offset(rip.x, rip.y),
                                radius = innerR,
                                style = Stroke(width = strokeW * 0.65f)
                            )
                        }
                    }
                }
            }

            // 4. Draw falling droplets
            if (shouldAnimate) {
                for (i in 0 until maxDroplets) {
                    val drop = droplets[i]
                    if (drop.isFalling && drop.y > -20f && drop.y < drop.targetSurfaceY) {
                        val startY = (drop.y - drop.length).coerceAtLeast(0f)
                        val endY = drop.y

                        // Droplet tail gradient
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    dropletColor.copy(alpha = 0.02f),
                                    dropletColor.copy(alpha = drop.alpha)
                                ),
                                startY = startY,
                                endY = endY
                            ),
                            start = Offset(drop.x, startY),
                            end = Offset(drop.x, endY),
                            strokeWidth = drop.width,
                            cap = StrokeCap.Round
                        )

                        // Droplet droplet bead head
                        drawCircle(
                            color = dropletColor.copy(alpha = (drop.alpha * 1.25f).coerceAtMost(0.5f)),
                            center = Offset(drop.x, endY),
                            radius = drop.width * 0.85f
                        )
                    }
                }
            }
        }

        // Foreground content
        content()
    }
}
