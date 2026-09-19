package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.BackgroundStyle
import com.example.data.local.ThemeAccent
import com.example.data.local.WaterColor
import com.example.data.local.WaterDropSettings

val LocalBackgroundStyle = compositionLocalOf { BackgroundStyle.DEFAULT }
val LocalSmoothAnimations = compositionLocalOf { true }
val LocalWaterDropSettings = compositionLocalOf { WaterDropSettings() }
val LocalHasAppBackground = compositionLocalOf { false }

fun getAccentColors(accent: ThemeAccent, isDark: Boolean): Pair<Color, Color> {
    return when (accent) {
        ThemeAccent.BLUE -> if (isDark) Pair(AccentBlueLight, Color(0xFF1E88E5)) else Pair(AccentBlue, AccentBlueLight)
        ThemeAccent.PURPLE -> if (isDark) Pair(AccentPurpleLight, Color(0xFF651FFF)) else Pair(AccentPurple, AccentPurpleLight)
        ThemeAccent.MAGENTA -> if (isDark) Pair(AccentMagentaLight, Color(0xFFC2185B)) else Pair(AccentMagenta, AccentMagentaLight)
        ThemeAccent.CORAL -> if (isDark) Pair(AccentCoralLight, Color(0xFFE64A19)) else Pair(AccentCoral, AccentCoralLight)
        ThemeAccent.EMERALD -> if (isDark) Pair(AccentEmeraldLight, Color(0xFF059669)) else Pair(AccentEmerald, AccentEmeraldLight)
        ThemeAccent.TEAL -> if (isDark) Pair(AccentTealLight, Color(0xFF00796B)) else Pair(AccentTeal, AccentTealLight)
        ThemeAccent.NAVY -> if (isDark) Pair(AccentNavyLight, Color(0xFF263238)) else Pair(AccentNavy, AccentNavyLight)
        ThemeAccent.AMBER -> if (isDark) Pair(AccentAmberLight, Color(0xFFFF8F00)) else Pair(AccentAmber, AccentAmberLight)
        ThemeAccent.CYAN -> if (isDark) Pair(Color(0xFF38BDF8), Color(0xFF0284C7)) else Pair(Color(0xFF0284C7), Color(0xFF38BDF8))
    }
}

fun createWaterDropColorScheme(isDark: Boolean, waterColor: WaterColor): androidx.compose.material3.ColorScheme {
    return if (isDark) {
        val primary = Color(waterColor.dropletHex)
        val secondary = Color(waterColor.secondaryHex)
        val surface = Color(waterColor.surfaceHex)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF001F33),
            primaryContainer = Color(0xFF0A324D),
            onPrimaryContainer = Color(0xFFBAE6FD),
            secondary = secondary,
            onSecondary = Color(0xFF062235),
            secondaryContainer = Color(0xFF10364F),
            onSecondaryContainer = Color(0xFFE0F2FE),
            tertiary = Color(waterColor.primaryHex),
            onTertiary = Color(0xFF001F28),
            background = Color(0xFF071421),
            onBackground = Color(0xFFF0F9FF),
            surface = surface,
            onSurface = Color(0xFFF0F9FF),
            surfaceVariant = Color(0xFF122E47),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
    } else {
        val primary = Color(waterColor.primaryHex)
        val secondary = Color(waterColor.secondaryHex)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0F2FE),
            onPrimaryContainer = Color(0xFF03446A),
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE0F7FA),
            onSecondaryContainer = Color(0xFF004D40),
            tertiary = Color(0xFF009688),
            onTertiary = Color.White,
            background = Color(0xFFF0F9FF),
            onBackground = Color(0xFF0C243C),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0C243C),
            surfaceVariant = Color(0xFFE2F1F8),
            onSurfaceVariant = Color(0xFF334E68)
        )
    }
}

fun createCustomColorScheme(accent: ThemeAccent, isDark: Boolean): androidx.compose.material3.ColorScheme {
    val (primary, secondary) = getAccentColors(accent, isDark)
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = CloudAmber80,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = CloudAmber40,
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F5F9)
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accent: ThemeAccent = ThemeAccent.BLUE,
    backgroundStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    smoothAnimations: Boolean = true,
    waterDropSettings: WaterDropSettings = WaterDropSettings(),
    content: @Composable () -> Unit,
) {
    val isWaterDrop = backgroundStyle == BackgroundStyle.WATER_DROP || accent == ThemeAccent.CYAN
    val colorScheme = when {
        isWaterDrop -> createWaterDropColorScheme(darkTheme, waterDropSettings.waterColor)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> createCustomColorScheme(accent, darkTheme)
    }

    CompositionLocalProvider(
        LocalBackgroundStyle provides backgroundStyle,
        LocalSmoothAnimations provides smoothAnimations,
        LocalWaterDropSettings provides waterDropSettings
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * Subtle dynamic background that adapts to the selected BackgroundStyle and dark/light theme.
 * High performance (pure shader/canvas brush, no image decoding overhead).
 */
@Composable
fun DynamicAppBackground(
    modifier: Modifier = Modifier,
    isViewerActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val alreadyProvided = LocalHasAppBackground.current
    if (alreadyProvided) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val bgStyle = LocalBackgroundStyle.current
    val waterDropSettings = LocalWaterDropSettings.current
    val isDark = isSystemInDarkTheme()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    CompositionLocalProvider(LocalHasAppBackground provides true) {
        if (bgStyle == BackgroundStyle.WATER_DROP) {
            WaterDropBackground(
                settings = waterDropSettings,
                isDark = isDark,
                isViewerActive = isViewerActive,
                modifier = modifier
            ) {
                content()
            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .drawBehind {
                        when (bgStyle) {
                            BackgroundStyle.DEFAULT -> {
                                drawRect(if (isDark) DarkBackground else Color(0xFFF8FAFC))
                            }
                            BackgroundStyle.GLASS -> {
                                // Frosted specular glass gradient
                                val baseColor = if (isDark) Color(0xFF0F141C) else Color(0xFFF1F5F9)
                                val highlight = if (isDark) primaryColor.copy(alpha = 0.08f) else primaryColor.copy(alpha = 0.06f)
                                drawRect(baseColor)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(highlight, Color.Transparent),
                                        center = Offset(size.width * 0.8f, size.height * 0.15f),
                                        radius = size.width * 0.7f
                                    )
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(highlight.copy(alpha = 0.04f), Color.Transparent),
                                        center = Offset(size.width * 0.2f, size.height * 0.85f),
                                        radius = size.width * 0.8f
                                    )
                                )
                            }
                            BackgroundStyle.GRADIENT -> {
                                // Modern diagonal smooth gradient
                                val start = if (isDark) Color(0xFF0F141C) else Color(0xFFF8FAFC)
                                val end = if (isDark) Color(0xFF192231) else Color(0xFFE2E8F0)
                                val tint = primaryColor.copy(alpha = if (isDark) 0.06f else 0.05f)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(start, tint, end),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    )
                                )
                            }
                            BackgroundStyle.NATURE -> {
                                // Subtle organic emerald / sage ambient glow
                                val base = if (isDark) Color(0xFF0E1714) else Color(0xFFF4F9F6)
                                drawRect(base)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF10B981).copy(alpha = 0.07f), Color.Transparent),
                                        center = Offset(size.width * 0.2f, size.height * 0.2f),
                                        radius = size.width * 0.8f
                                    )
                                )
                            }
                            BackgroundStyle.MINIMAL -> {
                                drawRect(if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA))
                            }
                            BackgroundStyle.BLUR -> {
                                val base = if (isDark) Color(0xFF0D1117) else Color(0xFFF8F9FB)
                                drawRect(base)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(primaryColor.copy(alpha = 0.09f), Color.Transparent),
                                        center = Offset(size.width * 0.5f, size.height * 0.4f),
                                        radius = size.width * 0.6f
                                    )
                                )
                            }
                            BackgroundStyle.DARK -> {
                                drawRect(Color(0xFF080C10))
                            }
                            BackgroundStyle.CHERRY_BLOSSOM -> {
                                val base = if (isDark) Color(0xFF180F14) else Color(0xFFFFF7F9)
                                drawRect(base)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFE91E63).copy(alpha = 0.07f), Color.Transparent),
                                        center = Offset(size.width * 0.7f, size.height * 0.2f),
                                        radius = size.width * 0.7f
                                    )
                                )
                            }
                            BackgroundStyle.WATER_DROP -> {
                                // Handled via WaterDropBackground
                            }
                        }
                    }
            ) {
                content()
            }
        }
    }
}

