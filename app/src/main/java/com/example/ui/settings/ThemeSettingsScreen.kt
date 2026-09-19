package com.example.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppThemeMode
import com.example.data.local.BackgroundStyle
import com.example.data.local.PreferencesManager
import com.example.data.local.PremiumTheme
import com.example.data.local.ThemeAccent
import com.example.data.local.ThemeAnimationIntensity
import com.example.data.local.ThemePreset
import com.example.data.local.WaterColor
import com.example.data.local.WaterDropSettings
import com.example.ui.theme.DynamicAppBackground
import kotlinx.coroutines.launch

/**
 * Unified Theme & Appearance Screen.
 *
 * Provides a single canonical theme manager where users can:
 * - Switch between Light / Dark / System appearance
 * - Enable, disable, or switch between Premium Animated Themes (Water Drop, Ice, Forest, City)
 * - Disable any active premium theme at any time and return immediately to Default/System
 * - Adjust theme animation toggle (ON/OFF) and intensity (Low/Medium/High)
 * - Customize colors, backgrounds, and effect options
 * - Reset all settings to Default Appearance with one tap
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    val currentThemeMode by preferencesManager.themeModeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
    val currentAccent by preferencesManager.themeAccentFlow.collectAsState(initial = ThemeAccent.BLUE)
    val currentBgStyle by preferencesManager.backgroundStyleFlow.collectAsState(initial = BackgroundStyle.DEFAULT)
    val currentPreset by preferencesManager.themePresetFlow.collectAsState(initial = ThemePreset.DEFAULT)
    val useDynamicColors by preferencesManager.useDynamicColorsFlow.collectAsState(initial = true)
    val blurBackground by preferencesManager.blurBackgroundFlow.collectAsState(initial = true)
    val smoothAnimations by preferencesManager.smoothAnimationsFlow.collectAsState(initial = true)
    val waterDropSettings by preferencesManager.waterDropSettingsFlow.collectAsState(initial = WaterDropSettings())

    // Single Canonical Source of Truth for Premium Themes
    val customThemeEnabled by preferencesManager.customThemeEnabledFlow.collectAsState(initial = false)
    val selectedCustomTheme by preferencesManager.selectedCustomThemeFlow.collectAsState(initial = PremiumTheme.WATER_DROP)
    val themeAnimationEnabled by preferencesManager.themeAnimationEnabledFlow.collectAsState(initial = true)
    val themeAnimationIntensity by preferencesManager.themeAnimationIntensityFlow.collectAsState(initial = ThemeAnimationIntensity.MEDIUM)

    DynamicAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Theme & Appearance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch { preferencesManager.resetToDefaultAppearance() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset to Default"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Appearance Mode (Light / Dark / System)
                item {
                    ThemeSectionCard(title = "Appearance Mode") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeModeSegment(
                                label = "Light",
                                icon = Icons.Default.LightMode,
                                isSelected = currentThemeMode == AppThemeMode.LIGHT,
                                onClick = {
                                    scope.launch { preferencesManager.setThemeMode(AppThemeMode.LIGHT) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeModeSegment(
                                label = "Dark",
                                icon = Icons.Default.DarkMode,
                                isSelected = currentThemeMode == AppThemeMode.DARK,
                                onClick = {
                                    scope.launch { preferencesManager.setThemeMode(AppThemeMode.DARK) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeModeSegment(
                                label = "System",
                                icon = Icons.Default.SettingsBrightness,
                                isSelected = currentThemeMode == AppThemeMode.SYSTEM,
                                onClick = {
                                    scope.launch { preferencesManager.setThemeMode(AppThemeMode.SYSTEM) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 2: Premium Animated Themes
                item {
                    ThemeSectionCard(
                        title = "Premium Themes",
                        subtitle = "Atmospheric visual styles with subtle ambient animations"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Status Banner
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (customThemeEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (customThemeEnabled) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (customThemeEnabled) {
                                                "Active: ${selectedCustomTheme.title}"
                                            } else {
                                                "Default / System Theme Active"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (customThemeEnabled) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch { preferencesManager.deactivatePremiumTheme() }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(30.dp)
                                                .testTag("deactivate_theme_header_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Disable Theme",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Theme Selector Cards (Water Drop, Ice, Forest, City)
                            PremiumTheme.values().forEach { theme ->
                                val isThisThemeActive = customThemeEnabled && selectedCustomTheme == theme
                                PremiumThemeSelectorCard(
                                    theme = theme,
                                    isActive = isThisThemeActive,
                                    onEnable = {
                                        scope.launch { preferencesManager.activatePremiumTheme(theme) }
                                    },
                                    onDisable = {
                                        scope.launch { preferencesManager.deactivatePremiumTheme() }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 3: Animation Controls (Only shown / relevant for active premium themes)
                item {
                    ThemeSectionCard(
                        title = "Theme Animation",
                        subtitle = "Movement effects and particle dynamics"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // 1. Master Animation Toggle
                            SettingToggleRow(
                                title = "Theme Animation",
                                subtitle = if (themeAnimationEnabled) "Subtle ambient motion active" else "Static appearance (animations paused)",
                                isChecked = themeAnimationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { preferencesManager.setThemeAnimationEnabled(enabled) }
                                }
                            )

                            // 2. Animation Intensity (Low / Medium / High)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Animation Intensity",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Adjust particle speed and density",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThemeAnimationIntensity.values().forEach { intensityOption ->
                                        val isSelected = themeAnimationIntensity == intensityOption
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            },
                                            border = if (isSelected) {
                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                            } else {
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clickable {
                                                    scope.launch {
                                                        preferencesManager.setThemeAnimationIntensity(intensityOption)
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = intensityOption.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Water Drop Specific Customizations (if Water Drop active)
                            if (customThemeEnabled && selectedCustomTheme == PremiumTheme.WATER_DROP) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                                SettingToggleRow(
                                    title = "Ripple Waves",
                                    subtitle = "Concentric expanding ripples when drops reach surface",
                                    isChecked = waterDropSettings.rippleEnabled,
                                    onCheckedChange = { ripple ->
                                        scope.launch { preferencesManager.setWaterDropRippleEnabled(ripple) }
                                    }
                                )

                                SettingToggleRow(
                                    title = "Translucent Caustics",
                                    subtitle = "Glass reflections and specular highlight depth",
                                    isChecked = waterDropSettings.blurEnabled,
                                    onCheckedChange = { blur ->
                                        scope.launch { preferencesManager.setWaterDropBlurEnabled(blur) }
                                    }
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Water Drop Palette",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(WaterColor.values()) { colorOption ->
                                            val isSelected = waterDropSettings.waterColor == colorOption
                                            val chipColor = Color(colorOption.primaryHex)
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) chipColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = if (isSelected) BorderStroke(1.5.dp, chipColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                modifier = Modifier
                                                    .clickable {
                                                        scope.launch { preferencesManager.setWaterDropColor(colorOption) }
                                                    }
                                                    .height(38.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(chipColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = colorOption.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = chipColor,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Color Palette (for Default/System mode)
                item {
                    ThemeSectionCard(
                        title = "Color Palette",
                        subtitle = if (customThemeEnabled) "Custom theme is active. Disable it to use standard accent colors."
                        else if (useDynamicColors) "Turn off dynamic colors to select a manual accent."
                        else "Choose primary accent color"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ThemeAccent.values()) { accent ->
                                val color = Color(accent.hexColor)
                                val isSelected = !useDynamicColors && !customThemeEnabled && currentAccent == accent

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                preferencesManager.setCustomThemeEnabled(false)
                                                preferencesManager.setUseDynamicColors(false)
                                                preferencesManager.setThemeAccent(accent)
                                            }
                                        }
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = accent.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 5: Standard Background Styles
                item {
                    ThemeSectionCard(
                        title = "Background Texture",
                        subtitle = if (customThemeEnabled) "Active when using Default/System mode" else "Subtle, eye-friendly ambient textures"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(BackgroundStyle.values()) { style ->
                                if (style == BackgroundStyle.WATER_DROP) return@items
                                val isSelected = !customThemeEnabled && currentBgStyle == style
                                BackgroundStylePill(
                                    style = style,
                                    isSelected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.setCustomThemeEnabled(false)
                                            preferencesManager.setBackgroundStyle(style)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 6: Options & Effects
                item {
                    ThemeSectionCard(title = "Options & Effects") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SettingToggleRow(
                                title = "Use Dynamic Colors",
                                subtitle = "Match colors with your Android system wallpaper",
                                isChecked = useDynamicColors && !customThemeEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        if (checked) {
                                            preferencesManager.setCustomThemeEnabled(false)
                                        }
                                        preferencesManager.setUseDynamicColors(checked)
                                    }
                                }
                            )

                            SettingToggleRow(
                                title = "Subtle Ambient Glow",
                                subtitle = "Soft backdrop gradients and frosted glass highlights",
                                isChecked = blurBackground,
                                onCheckedChange = { checked ->
                                    scope.launch { preferencesManager.setBlurBackground(checked) }
                                }
                            )

                            SettingToggleRow(
                                title = "Smooth Transitions",
                                subtitle = "Fluid motion across gallery grid and media viewer",
                                isChecked = smoothAnimations,
                                onCheckedChange = { checked ->
                                    scope.launch { preferencesManager.setSmoothAnimations(checked) }
                                }
                            )
                        }
                    }
                }

                // Section 7: Curated Theme Presets
                item {
                    ThemeSectionCard(
                        title = "Quick Presets",
                        subtitle = "One-tap handcrafted visual styles"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ThemePreset.values()) { preset ->
                                ThemePresetCard(
                                    preset = preset,
                                    isSelected = !customThemeEnabled && currentPreset == preset && !useDynamicColors,
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.setThemePreset(preset)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 8: Reset to Default Action
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { preferencesManager.resetToDefaultAppearance() }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Restore Default Appearance",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Reset back to System theme, standard blue accent, and default background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium Theme Selector Card.
 *
 * Displays preview, title, description, and clear Enable / Disable controls.
 */
@Composable
private fun PremiumThemeSelectorCard(
    theme: PremiumTheme,
    isActive: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    val themeAccentColor = when (theme) {
        PremiumTheme.WATER_DROP -> Color(0xFF00B4D8)
        PremiumTheme.ICE -> Color(0xFF38BDF8)
        PremiumTheme.FOREST -> Color(0xFF10B981)
        PremiumTheme.CITY -> Color(0xFFF59E0B)
    }

    val previewBrush = when (theme) {
        PremiumTheme.WATER_DROP -> Brush.linearGradient(
            listOf(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF38BDF8))
        )
        PremiumTheme.ICE -> Brush.linearGradient(
            listOf(Color(0xFF0369A1), Color(0xFF38BDF8), Color(0xFFBAE6FD))
        )
        PremiumTheme.FOREST -> Brush.linearGradient(
            listOf(Color(0xFF064E3B), Color(0xFF059669), Color(0xFF34D399))
        )
        PremiumTheme.CITY -> Brush.linearGradient(
            listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFFF59E0B))
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                themeAccentColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = BorderStroke(
            width = if (isActive) 1.8.dp else 1.dp,
            color = if (isActive) themeAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("premium_theme_card_${theme.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme visual preview box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (theme == PremiumTheme.WATER_DROP) Icons.Default.WaterDrop else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = theme.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = themeAccentColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, themeAccentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "PREMIUM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = themeAccentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = theme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Row: Shows Active status and Disable button, or Enable button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = themeAccentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.2.dp, themeAccentColor)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = themeAccentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = themeAccentColor
                            )
                        }
                    }

                    // Explicit DISABLE button - user is NEVER stuck!
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("disable_theme_${theme.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Disable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    // ENABLE button
                    Button(
                        onClick = onEnable,
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("enable_theme_${theme.name.lowercase()}")
                    ) {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun ThemeModeSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
            .clickable(onClick = onClick)
            .height(54.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun BackgroundStylePill(
    style: BackgroundStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = Color(preset.accent.hexColor)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (preset.mode == AppThemeMode.DARK) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (preset.mode == AppThemeMode.DARK) Color.White else Color.Black
            )
            Text(
                text = preset.mode.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = if (preset.mode == AppThemeMode.DARK) Color.LightGray else Color.DarkGray
            )
        }
    }
}
