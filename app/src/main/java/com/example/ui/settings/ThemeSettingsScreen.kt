package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.data.local.ThemeAccent
import com.example.data.local.ThemePreset
import com.example.ui.theme.DynamicAppBackground
import kotlinx.coroutines.launch

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

    DynamicAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Theme Customization",
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
                // Section 1: Theme Mode (Light / Dark / System)
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

                // Section 2: Color Palette
                item {
                    ThemeSectionCard(
                        title = "Color Palette",
                        subtitle = if (useDynamicColors) "Turn off dynamic colors to use custom palette" else "Choose primary accent color"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ThemeAccent.values()) { accent ->
                                val color = Color(accent.hexColor)
                                val isSelected = !useDynamicColors && currentAccent == accent

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
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

                // Section 3: Background Style
                item {
                    ThemeSectionCard(
                        title = "Background Style",
                        subtitle = "Subtle, eye-friendly ambient textures"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(BackgroundStyle.values()) { style ->
                                val isSelected = currentBgStyle == style
                                BackgroundStylePill(
                                    style = style,
                                    isSelected = isSelected,
                                    onClick = {
                                        scope.launch { preferencesManager.setBackgroundStyle(style) }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 4: Display & Effect Toggles
                item {
                    ThemeSectionCard(title = "Options & Effects") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SettingToggleRow(
                                title = "Use Dynamic Colors",
                                subtitle = "Match colors with your Android system wallpaper",
                                isChecked = useDynamicColors,
                                onCheckedChange = { checked ->
                                    scope.launch { preferencesManager.setUseDynamicColors(checked) }
                                }
                            )

                            SettingToggleRow(
                                title = "Subtle Ambient Glow",
                                subtitle = "Soft backdrop gradients and frosted effects",
                                isChecked = blurBackground,
                                onCheckedChange = { checked ->
                                    scope.launch { preferencesManager.setBlurBackground(checked) }
                                }
                            )

                            SettingToggleRow(
                                title = "Smooth Animations",
                                subtitle = "Fluid transitions across gallery and viewer",
                                isChecked = smoothAnimations,
                                onCheckedChange = { checked ->
                                    scope.launch { preferencesManager.setSmoothAnimations(checked) }
                                }
                            )
                        }
                    }
                }

                // Section 5: Curated Theme Presets
                item {
                    ThemeSectionCard(
                        title = "Theme Presets",
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
                                    isSelected = currentPreset == preset && !useDynamicColors,
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
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
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
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
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
