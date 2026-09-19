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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ThemeAnimationIntensity
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-allocated particle for Forest theme (ambient forest spore / floating leaf).
 */
private class ForestParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 2.0f,
    var speedY: Float = 12f,
    var swaySpeed: Float = 1.0f,
    var swayAmplitude: Float = 10f,
    var phase: Float = 0f,
    var alpha: Float = 0.25f
)

/**
 * Premium Forest dynamic theme background.
 *
 * Visual characteristics:
 * - Deep emerald and moss green natural atmosphere
 * - Soft diagonal crepuscular sunbeams (light rays)
 * - Gentle canopy / organic silhouette depth layer
 * - Ambient floating forest particles with calm natural sway
 *
 * Performance boundaries:
 * - Zero GC allocation in render loop
 * - Pauses when backgrounded or in reduced-motion mode
 * - Calm, readable, and non-distracting
 */
@Composable
fun ForestThemeBackground(
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
    val particleCount = when (effectiveIntensity) {
        ThemeAnimationIntensity.LOW -> 8
        ThemeAnimationIntensity.MEDIUM -> 14
        ThemeAnimationIntensity.HIGH -> 22
    }

    val particles = remember(particleCount) {
        Array(particleCount) { ForestParticle() }
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

                for (i in 0 until particleCount) {
                    val p = particles[i]
                    p.phase += dt * p.swaySpeed
                    p.y -= p.speedY * effectiveIntensity.speedMultiplier * dt // floating gently upward
                    p.x += (sin(p.phase) * p.swayAmplitude) * dt
                }

                frameTick = nowNanos
            }
        }
    }

    // Forest color palette
    val bgColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF08140C), // Deep pine shadow
                Color(0xFF0F2215),
                Color(0xFF09160E)
            )
        } else {
            listOf(
                Color(0xFFF1F8F4), // Morning forest mist
                Color(0xFFE2EFE7),
                Color(0xFFF1F8F4)
            )
        }
    }

    val leafColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val sunbeamColor = if (isDark) Color(0xFFFDE68A) else Color(0xFFFEF08A)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // Init particles
            for (i in 0 until particleCount) {
                val p = particles[i]
                if (p.x == 0f && p.y == 0f) {
                    p.x = Random.nextFloat() * width
                    p.y = Random.nextFloat() * height
                    p.radius = Random.nextFloat() * 1.8f + 1.2f
                    p.speedY = Random.nextFloat() * 10f + 8f
                    p.swaySpeed = Random.nextFloat() * 1.2f + 0.6f
                    p.swayAmplitude = Random.nextFloat() * 8f + 6f
                    p.phase = Random.nextFloat() * 6.28f
                    p.alpha = Random.nextFloat() * 0.16f + 0.14f
                }
                if (p.y < -20f) {
                    p.y = height + 10f
                    p.x = Random.nextFloat() * width
                }
            }

            // 1. Draw base forest gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = bgColors,
                    startY = 0f,
                    endY = height
                )
            )

            // 2. Soft diagonal crepuscular sunbeams (light rays)
            val currentNanos = if (shouldAnimate) frameTick else 0L
            val timeSec = if (currentNanos > 0L) (currentNanos / 1_000_000_000.0) % 60.0 else 0.0
            val beamShift = (sin(timeSec * 0.2).toFloat() * 0.05f)

            val sunbeam1 = Path().apply {
                moveTo(width * (0.05f + beamShift), 0f)
                lineTo(width * (0.35f + beamShift), 0f)
                lineTo(width * (0.65f + beamShift), height)
                lineTo(width * (0.25f + beamShift), height)
                close()
            }
            drawPath(
                path = sunbeam1,
                brush = Brush.linearGradient(
                    colors = listOf(
                        sunbeamColor.copy(alpha = if (isDark) 0.045f else 0.065f),
                        sunbeamColor.copy(alpha = if (isDark) 0.015f else 0.025f),
                        Color.Transparent
                    ),
                    start = Offset(width * 0.2f, 0f),
                    end = Offset(width * 0.5f, height)
                )
            )

            // 3. Subtle bottom silhouette of tranquil tree/canopy contours
            val canopyPath = Path().apply {
                moveTo(0f, height)
                lineTo(0f, height * 0.88f)
                cubicTo(
                    width * 0.25f, height * 0.84f,
                    width * 0.40f, height * 0.90f,
                    width * 0.65f, height * 0.86f
                )
                cubicTo(
                    width * 0.80f, height * 0.82f,
                    width * 0.92f, height * 0.87f,
                    width, height * 0.85f
                )
                lineTo(width, height)
                close()
            }
            drawPath(
                path = canopyPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        leafColor.copy(alpha = if (isDark) 0.035f else 0.05f),
                        leafColor.copy(alpha = if (isDark) 0.07f else 0.09f)
                    ),
                    startY = height * 0.84f,
                    endY = height
                )
            )

            // 4. Draw gentle floating particles (leaf/spore dots)
            if (shouldAnimate) {
                for (i in 0 until particleCount) {
                    val p = particles[i]
                    drawCircle(
                        color = if (i % 2 == 0) leafColor.copy(alpha = p.alpha) else sunbeamColor.copy(alpha = p.alpha * 0.8f),
                        center = Offset(p.x, p.y),
                        radius = p.radius
                    )
                }
            }
        }

        content()
    }
}
