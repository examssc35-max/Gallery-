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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ThemeAnimationIntensity
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-allocated crystal frost particle for Ice theme.
 */
private class FrostParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 1.8f,
    var speedY: Float = 18f,
    var driftSpeedX: Float = 6f,
    var phase: Float = 0f,
    var alpha: Float = 0.3f
)

/**
 * Premium Ice dynamic theme background.
 *
 * Visual characteristics:
 * - Subzero glacial blue-white crystalline palette
 * - Frosted glass surfaces with subtle geometric ice facets
 * - Soft drifting glacial light reflections
 * - Micro crystalline frost particles gently floating downwards
 *
 * Performance boundaries:
 * - Zero GC in draw loop
 * - Pauses when backgrounded or in reduced-motion mode
 * - Minimal GPU footprint
 */
@Composable
fun IceThemeBackground(
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
        ThemeAnimationIntensity.MEDIUM -> 16
        ThemeAnimationIntensity.HIGH -> 26
    }

    val particles = remember(particleCount) {
        Array(particleCount) { FrostParticle() }
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
                    p.phase += dt * 1.2f
                    p.y += p.speedY * effectiveIntensity.speedMultiplier * dt
                    p.x += (sin(p.phase) * p.driftSpeedX) * dt
                }

                frameTick = nowNanos
            }
        }
    }

    // Glacial palette colors
    val bgColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF09121F), // Deep glacial twilight
                Color(0xFF0D1B2D),
                Color(0xFF081422)
            )
        } else {
            listOf(
                Color(0xFFF1F7FD), // Soft alpine ice mist
                Color(0xFFE2EFFC),
                Color(0xFFF1F7FD)
            )
        }
    }

    val crystalShineColor = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0284C7)
    val frostFacetColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF93C5FD)

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
                    p.radius = Random.nextFloat() * 1.6f + 1.2f
                    p.speedY = (Random.nextFloat() * 14f + 12f)
                    p.driftSpeedX = (Random.nextFloat() * 8f + 4f)
                    p.phase = Random.nextFloat() * 6.28f
                    p.alpha = Random.nextFloat() * 0.18f + 0.15f
                }
                if (p.y > height + 20f) {
                    p.y = -10f
                    p.x = Random.nextFloat() * width
                }
            }

            // 1. Draw base glacial gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = bgColors,
                    startY = 0f,
                    endY = height
                )
            )

            // 2. Subtle frosted crystalline facets (geometric ice planes)
            val facet1 = Path().apply {
                moveTo(width * 0.10f, 0f)
                lineTo(width * 0.75f, 0f)
                lineTo(width * 0.40f, height * 0.28f)
                close()
            }
            drawPath(
                path = facet1,
                brush = Brush.linearGradient(
                    colors = listOf(
                        frostFacetColor.copy(alpha = if (isDark) 0.05f else 0.08f),
                        Color.Transparent
                    ),
                    start = Offset(width * 0.4f, 0f),
                    end = Offset(width * 0.4f, height * 0.28f)
                )
            )

            val facet2 = Path().apply {
                moveTo(width, height * 0.45f)
                lineTo(width * 0.35f, height * 0.75f)
                lineTo(width, height * 0.88f)
                close()
            }
            drawPath(
                path = facet2,
                brush = Brush.linearGradient(
                    colors = listOf(
                        crystalShineColor.copy(alpha = if (isDark) 0.04f else 0.06f),
                        Color.Transparent
                    ),
                    start = Offset(width * 0.35f, height * 0.6f),
                    end = Offset(width, height * 0.7f)
                )
            )

            // 3. Ambient glacial light reflection (slowly moving shimmer)
            val currentNanos = if (shouldAnimate) frameTick else 0L
            val timeSec = if (currentNanos > 0L) (currentNanos / 1_000_000_000.0) % 60.0 else 0.0
            val reflectionX = width * (0.60f + 0.08f * sin(timeSec * 0.3).toFloat())
            val reflectionY = height * (0.30f + 0.06f * sin(timeSec * 0.25).toFloat())

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        crystalShineColor.copy(alpha = if (isDark) 0.08f else 0.12f),
                        crystalShineColor.copy(alpha = if (isDark) 0.02f else 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(reflectionX, reflectionY),
                    radius = width * 0.75f
                ),
                center = Offset(reflectionX, reflectionY),
                radius = width * 0.75f
            )

            // 4. Draw micro crystalline frost particles
            if (shouldAnimate) {
                for (i in 0 until particleCount) {
                    val p = particles[i]
                    // Tiny 4-point crystal sparkle or soft circle
                    drawCircle(
                        color = crystalShineColor.copy(alpha = p.alpha),
                        center = Offset(p.x, p.y),
                        radius = p.radius
                    )
                    // Cross glimmer on select particles
                    if (i % 3 == 0) {
                        val arm = p.radius * 2.2f
                        drawLine(
                            color = crystalShineColor.copy(alpha = p.alpha * 0.6f),
                            start = Offset(p.x - arm, p.y),
                            end = Offset(p.x + arm, p.y),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = crystalShineColor.copy(alpha = p.alpha * 0.6f),
                            start = Offset(p.x, p.y - arm),
                            end = Offset(p.x, p.y + arm),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }

        content()
    }
}
