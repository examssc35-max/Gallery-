package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.MediaCategory
import com.example.domain.model.StorageUsage
import com.example.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloudgallery_settings")

data class R2Credentials(
    val accountId: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucketName: String = "",
    val endpoint: String = "",
    val isVerified: Boolean = false
)

data class BackupSettings(
    val isAutoBackupEnabled: Boolean = false,
    val backupVideos: Boolean = true,
    val backupPhotos: Boolean = true,
    val wifiOnly: Boolean = true,
    val requireCharging: Boolean = false,
    val lastBackupTime: Long = 0L
)

enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ThemeAccent(val label: String, val hexColor: Long) {
    BLUE("Blue", 0xFF1976D2),
    PURPLE("Purple", 0xFF7C4DFF),
    MAGENTA("Magenta", 0xFFE91E63),
    CORAL("Coral", 0xFFFF5722),
    EMERALD("Emerald", 0xFF10B981),
    TEAL("Teal", 0xFF00897B),
    NAVY("Navy", 0xFF37474F),
    AMBER("Amber", 0xFFFFA000),
    CYAN("Cyan", 0xFF00B4D8)
}

enum class BackgroundStyle(val label: String) {
    DEFAULT("Default"),
    GLASS("Glass"),
    GRADIENT("Gradient"),
    NATURE("Nature"),
    MINIMAL("Minimal"),
    BLUR("Blur"),
    DARK("Dark"),
    CHERRY_BLOSSOM("Cherry Blossom"),
    WATER_DROP("Water Drop")
}

enum class ThemePreset(
    val label: String,
    val mode: AppThemeMode,
    val accent: ThemeAccent,
    val bgStyle: BackgroundStyle
) {
    DEFAULT("Default", AppThemeMode.SYSTEM, ThemeAccent.BLUE, BackgroundStyle.DEFAULT),
    LIGHT("Light", AppThemeMode.LIGHT, ThemeAccent.BLUE, BackgroundStyle.DEFAULT),
    DARK("Dark", AppThemeMode.DARK, ThemeAccent.BLUE, BackgroundStyle.DARK),
    OCEAN("Ocean", AppThemeMode.DARK, ThemeAccent.TEAL, BackgroundStyle.GRADIENT),
    FOREST("Forest", AppThemeMode.DARK, ThemeAccent.EMERALD, BackgroundStyle.NATURE),
    SUNSET("Sunset", AppThemeMode.DARK, ThemeAccent.CORAL, BackgroundStyle.GRADIENT),
    PURPLE("Purple", AppThemeMode.DARK, ThemeAccent.PURPLE, BackgroundStyle.BLUR),
    MINIMAL("Minimal", AppThemeMode.LIGHT, ThemeAccent.NAVY, BackgroundStyle.MINIMAL),
    GLASS("Glass", AppThemeMode.DARK, ThemeAccent.BLUE, BackgroundStyle.GLASS),
    NIGHT("Night", AppThemeMode.DARK, ThemeAccent.BLUE, BackgroundStyle.DARK),
    CHERRY_BLOSSOM("Cherry Blossom", AppThemeMode.LIGHT, ThemeAccent.MAGENTA, BackgroundStyle.CHERRY_BLOSSOM),
    WATER_DROP("Water Drop", AppThemeMode.DARK, ThemeAccent.CYAN, BackgroundStyle.WATER_DROP)
}

enum class WaterAnimationIntensity(val label: String, val dropCount: Int, val speedMultiplier: Float) {
    LOW("Low", dropCount = 4, speedMultiplier = 0.65f),
    MEDIUM("Medium", dropCount = 8, speedMultiplier = 1.0f),
    HIGH("High", dropCount = 14, speedMultiplier = 1.35f)
}

enum class WaterColor(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val surfaceHex: Long,
    val dropletHex: Long
) {
    CYAN_LAGOON(
        label = "Cyan Lagoon",
        primaryHex = 0xFF00B4D8,
        secondaryHex = 0xFF90E0EF,
        surfaceHex = 0xFF091E2E,
        dropletHex = 0xFF38BDF8
    ),
    DEEP_OCEAN(
        label = "Deep Ocean",
        primaryHex = 0xFF0077B6,
        secondaryHex = 0xFF023E8A,
        surfaceHex = 0xFF061423,
        dropletHex = 0xFF0096C7
    ),
    CRYSTAL_CLEAR(
        label = "Crystal Clear",
        primaryHex = 0xFF38BDF8,
        secondaryHex = 0xFFBAE6FD,
        surfaceHex = 0xFF081C2E,
        dropletHex = 0xFF7DD3FC
    ),
    ARCTIC_FROST(
        label = "Arctic Frost",
        primaryHex = 0xFF06B6D4,
        secondaryHex = 0xFFCFFAFE,
        surfaceHex = 0xFF051C26,
        dropletHex = 0xFF22D3EE
    )
}

data class WaterDropSettings(
    val animationEnabled: Boolean = true,
    val intensity: WaterAnimationIntensity = WaterAnimationIntensity.MEDIUM,
    val rippleEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
    val waterColor: WaterColor = WaterColor.CYAN_LAGOON
)

class PreferencesManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager = KeystoreManager()
) {
    companion object {
        val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_ACCENT = stringPreferencesKey("theme_accent")
        val KEY_BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val KEY_THEME_PRESET = stringPreferencesKey("theme_preset")
        val KEY_USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val KEY_BLUR_BACKGROUND = booleanPreferencesKey("blur_background")
        val KEY_SMOOTH_ANIMATIONS = booleanPreferencesKey("smooth_animations")

        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_BACKUP_VIDEOS = booleanPreferencesKey("backup_videos")
        val KEY_BACKUP_PHOTOS = booleanPreferencesKey("backup_photos")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")

        val KEY_R2_ACCOUNT_ID = stringPreferencesKey("r2_account_id")
        val KEY_R2_ACCESS_KEY_ID = stringPreferencesKey("r2_access_key_id")
        val KEY_R2_ENCRYPTED_SECRET = stringPreferencesKey("r2_encrypted_secret")
        val KEY_R2_BUCKET_NAME = stringPreferencesKey("r2_bucket_name")
        val KEY_R2_ENDPOINT = stringPreferencesKey("r2_endpoint")
        val KEY_R2_IS_VERIFIED = booleanPreferencesKey("r2_is_verified")

        // Cloud Storage Usage Cache
        val KEY_STORAGE_HAS_CACHE = booleanPreferencesKey("storage_has_cache")
        val KEY_STORAGE_TOTAL_BYTES = longPreferencesKey("storage_total_bytes")
        val KEY_STORAGE_PHOTO_BYTES = longPreferencesKey("storage_photo_bytes")
        val KEY_STORAGE_VIDEO_BYTES = longPreferencesKey("storage_video_bytes")
        val KEY_STORAGE_OTHER_BYTES = longPreferencesKey("storage_other_bytes")
        val KEY_STORAGE_PHOTO_COUNT = intPreferencesKey("storage_photo_count")
        val KEY_STORAGE_VIDEO_COUNT = intPreferencesKey("storage_video_count")
        val KEY_STORAGE_OTHER_COUNT = intPreferencesKey("storage_other_count")
        val KEY_STORAGE_TOTAL_COUNT = intPreferencesKey("storage_total_count")
        val KEY_STORAGE_LAST_UPDATED = longPreferencesKey("storage_last_updated")

        // AI Assistant Settings
        val KEY_AI_PROVIDER_MODE = stringPreferencesKey("ai_provider_mode")
        val KEY_AI_GEMINI_API_KEY = stringPreferencesKey("ai_gemini_api_key")

        // Smart Collections Settings
        val KEY_SMART_COLLECTIONS_ENABLED = booleanPreferencesKey("smart_collections_enabled")
        val KEY_SMART_COLLECTIONS_AUTO_ANALYZE = booleanPreferencesKey("smart_collections_auto_analyze")
        val KEY_SMART_COLLECTIONS_ANALYZE_VIDEOS = booleanPreferencesKey("smart_collections_analyze_videos")
        val KEY_SMART_COLLECTIONS_WIFI_ONLY = booleanPreferencesKey("smart_collections_wifi_only")
        val KEY_SMART_COLLECTIONS_REQUIRE_CHARGING = booleanPreferencesKey("smart_collections_require_charging")
        val KEY_SMART_COLLECTIONS_MODEL_INSTALLED = booleanPreferencesKey("smart_collections_model_installed")
        val KEY_SMART_COLLECTIONS_MODEL_VERSION = stringPreferencesKey("smart_collections_model_version")

        // Water Drop Theme Settings
        val KEY_WATER_DROP_ANIMATION_ENABLED = booleanPreferencesKey("water_drop_anim_enabled")
        val KEY_WATER_DROP_INTENSITY = stringPreferencesKey("water_drop_intensity")
        val KEY_WATER_DROP_RIPPLE_ENABLED = booleanPreferencesKey("water_drop_ripple_enabled")
        val KEY_WATER_DROP_BLUR_ENABLED = booleanPreferencesKey("water_drop_blur_enabled")
        val KEY_WATER_DROP_COLOR = stringPreferencesKey("water_drop_color")
    }

    val gridColumnsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_GRID_COLUMNS] ?: 3
    }

    val sortOrderFlow: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        try {
            SortOrder.valueOf(prefs[KEY_SORT_ORDER] ?: SortOrder.DATE_DESC.name)
        } catch (_: Exception) {
            SortOrder.DATE_DESC
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        try {
            AppThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    val themeAccentFlow: Flow<ThemeAccent> = context.dataStore.data.map { prefs ->
        try {
            ThemeAccent.valueOf(prefs[KEY_THEME_ACCENT] ?: ThemeAccent.BLUE.name)
        } catch (_: Exception) {
            ThemeAccent.BLUE
        }
    }

    val backgroundStyleFlow: Flow<BackgroundStyle> = context.dataStore.data.map { prefs ->
        try {
            BackgroundStyle.valueOf(prefs[KEY_BACKGROUND_STYLE] ?: BackgroundStyle.DEFAULT.name)
        } catch (_: Exception) {
            BackgroundStyle.DEFAULT
        }
    }

    val themePresetFlow: Flow<ThemePreset> = context.dataStore.data.map { prefs ->
        try {
            ThemePreset.valueOf(prefs[KEY_THEME_PRESET] ?: ThemePreset.DEFAULT.name)
        } catch (_: Exception) {
            ThemePreset.DEFAULT
        }
    }

    val useDynamicColorsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_DYNAMIC_COLORS] ?: true
    }

    val blurBackgroundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLUR_BACKGROUND] ?: true
    }

    val smoothAnimationsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMOOTH_ANIMATIONS] ?: true
    }

    val waterDropAnimationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATER_DROP_ANIMATION_ENABLED] ?: true
    }

    val waterDropIntensityFlow: Flow<WaterAnimationIntensity> = context.dataStore.data.map { prefs ->
        try {
            WaterAnimationIntensity.valueOf(prefs[KEY_WATER_DROP_INTENSITY] ?: WaterAnimationIntensity.MEDIUM.name)
        } catch (_: Exception) {
            WaterAnimationIntensity.MEDIUM
        }
    }

    val waterDropRippleEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATER_DROP_RIPPLE_ENABLED] ?: true
    }

    val waterDropBlurEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATER_DROP_BLUR_ENABLED] ?: true
    }

    val waterDropColorFlow: Flow<WaterColor> = context.dataStore.data.map { prefs ->
        try {
            WaterColor.valueOf(prefs[KEY_WATER_DROP_COLOR] ?: WaterColor.CYAN_LAGOON.name)
        } catch (_: Exception) {
            WaterColor.CYAN_LAGOON
        }
    }

    val waterDropSettingsFlow: Flow<WaterDropSettings> = context.dataStore.data.map { prefs ->
        val intensity = try {
            WaterAnimationIntensity.valueOf(prefs[KEY_WATER_DROP_INTENSITY] ?: WaterAnimationIntensity.MEDIUM.name)
        } catch (_: Exception) {
            WaterAnimationIntensity.MEDIUM
        }
        val waterColor = try {
            WaterColor.valueOf(prefs[KEY_WATER_DROP_COLOR] ?: WaterColor.CYAN_LAGOON.name)
        } catch (_: Exception) {
            WaterColor.CYAN_LAGOON
        }
        WaterDropSettings(
            animationEnabled = prefs[KEY_WATER_DROP_ANIMATION_ENABLED] ?: true,
            intensity = intensity,
            rippleEnabled = prefs[KEY_WATER_DROP_RIPPLE_ENABLED] ?: true,
            blurEnabled = prefs[KEY_WATER_DROP_BLUR_ENABLED] ?: true,
            waterColor = waterColor
        )
    }

    val backupSettingsFlow: Flow<BackupSettings> = context.dataStore.data.map { prefs ->
        BackupSettings(
            isAutoBackupEnabled = prefs[KEY_AUTO_BACKUP_ENABLED] ?: false,
            backupVideos = prefs[KEY_BACKUP_VIDEOS] ?: true,
            backupPhotos = prefs[KEY_BACKUP_PHOTOS] ?: true,
            wifiOnly = prefs[KEY_WIFI_ONLY] ?: true,
            requireCharging = prefs[KEY_REQUIRE_CHARGING] ?: false,
            lastBackupTime = prefs[KEY_LAST_BACKUP_TIME] ?: 0L
        )
    }

    val r2CredentialsFlow: Flow<R2Credentials> = context.dataStore.data.map { prefs ->
        val encryptedSecret = prefs[KEY_R2_ENCRYPTED_SECRET] ?: ""
        val decryptedSecret = if (encryptedSecret.isNotEmpty()) {
            keystoreManager.decrypt(encryptedSecret)
        } else {
            ""
        }
        R2Credentials(
            accountId = prefs[KEY_R2_ACCOUNT_ID] ?: "",
            accessKeyId = prefs[KEY_R2_ACCESS_KEY_ID] ?: "",
            secretAccessKey = decryptedSecret,
            bucketName = prefs[KEY_R2_BUCKET_NAME] ?: "",
            endpoint = prefs[KEY_R2_ENDPOINT] ?: "",
            isVerified = prefs[KEY_R2_IS_VERIFIED] ?: false
        )
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GRID_COLUMNS] = columns
        }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SORT_ORDER] = sortOrder.name
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setThemeAccent(accent: ThemeAccent) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_ACCENT] = accent.name
        }
    }

    suspend fun setBackgroundStyle(style: BackgroundStyle) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_STYLE] = style.name
        }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_PRESET] = preset.name
            prefs[KEY_THEME_MODE] = preset.mode.name
            prefs[KEY_THEME_ACCENT] = preset.accent.name
            prefs[KEY_BACKGROUND_STYLE] = preset.bgStyle.name
            prefs[KEY_USE_DYNAMIC_COLORS] = false
        }
    }

    suspend fun setUseDynamicColors(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLORS] = enabled
        }
    }

    suspend fun setBlurBackground(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLUR_BACKGROUND] = enabled
        }
    }

    suspend fun setSmoothAnimations(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMOOTH_ANIMATIONS] = enabled
        }
    }

    suspend fun setWaterDropAnimationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATER_DROP_ANIMATION_ENABLED] = enabled
        }
    }

    suspend fun setWaterDropIntensity(intensity: WaterAnimationIntensity) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATER_DROP_INTENSITY] = intensity.name
        }
    }

    suspend fun setWaterDropRippleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATER_DROP_RIPPLE_ENABLED] = enabled
        }
    }

    suspend fun setWaterDropBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATER_DROP_BLUR_ENABLED] = enabled
        }
    }

    suspend fun setWaterDropColor(color: WaterColor) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATER_DROP_COLOR] = color.name
        }
    }

    suspend fun activateWaterDropTheme() {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_PRESET] = ThemePreset.WATER_DROP.name
            prefs[KEY_BACKGROUND_STYLE] = BackgroundStyle.WATER_DROP.name
            prefs[KEY_THEME_ACCENT] = ThemeAccent.CYAN.name
            prefs[KEY_USE_DYNAMIC_COLORS] = false
        }
    }

    suspend fun updateBackupSettings(settings: BackupSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_BACKUP_ENABLED] = settings.isAutoBackupEnabled
            prefs[KEY_BACKUP_VIDEOS] = settings.backupVideos
            prefs[KEY_BACKUP_PHOTOS] = settings.backupPhotos
            prefs[KEY_WIFI_ONLY] = settings.wifiOnly
            prefs[KEY_REQUIRE_CHARGING] = settings.requireCharging
            prefs[KEY_LAST_BACKUP_TIME] = settings.lastBackupTime
        }
    }

    suspend fun saveR2Credentials(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String,
        endpoint: String = "",
        isVerified: Boolean = false
    ) {
        val encryptedSecret = if (secretAccessKey.isNotEmpty()) {
            keystoreManager.encrypt(secretAccessKey)
        } else {
            ""
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_R2_ACCOUNT_ID] = accountId.trim()
            prefs[KEY_R2_ACCESS_KEY_ID] = accessKeyId.trim()
            if (encryptedSecret.isNotEmpty()) {
                prefs[KEY_R2_ENCRYPTED_SECRET] = encryptedSecret
            }
            prefs[KEY_R2_BUCKET_NAME] = bucketName.trim()
            prefs[KEY_R2_ENDPOINT] = endpoint.trim()
            prefs[KEY_R2_IS_VERIFIED] = isVerified
        }
    }

    suspend fun setR2Verified(isVerified: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_R2_IS_VERIFIED] = isVerified
        }
    }

    suspend fun clearR2Credentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_R2_ACCOUNT_ID)
            prefs.remove(KEY_R2_ACCESS_KEY_ID)
            prefs.remove(KEY_R2_ENCRYPTED_SECRET)
            prefs.remove(KEY_R2_BUCKET_NAME)
            prefs.remove(KEY_R2_ENDPOINT)
            prefs[KEY_R2_IS_VERIFIED] = false
        }
        keystoreManager.clearKey()
        clearStorageUsageCache()
    }

    val cachedStorageUsageFlow: Flow<StorageUsage?> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_STORAGE_HAS_CACHE] == true) {
            val photoBytes = prefs[KEY_STORAGE_PHOTO_BYTES] ?: 0L
            val videoBytes = prefs[KEY_STORAGE_VIDEO_BYTES] ?: 0L
            val otherBytes = prefs[KEY_STORAGE_OTHER_BYTES] ?: 0L
            val photoCount = prefs[KEY_STORAGE_PHOTO_COUNT] ?: 0
            val videoCount = prefs[KEY_STORAGE_VIDEO_COUNT] ?: 0
            val otherCount = prefs[KEY_STORAGE_OTHER_COUNT] ?: 0
            val totalBytes = prefs[KEY_STORAGE_TOTAL_BYTES] ?: (photoBytes + videoBytes + otherBytes)
            val totalCount = prefs[KEY_STORAGE_TOTAL_COUNT] ?: (photoCount + videoCount + otherCount)
            val lastUpdated = prefs[KEY_STORAGE_LAST_UPDATED] ?: 0L

            StorageUsage(
                totalBytes = totalBytes,
                photoBytes = photoBytes,
                videoBytes = videoBytes,
                otherBytes = otherBytes,
                photoCount = photoCount,
                videoCount = videoCount,
                otherCount = otherCount,
                totalObjectCount = totalCount,
                lastUpdated = lastUpdated,
                isCached = true
            )
        } else {
            null
        }
    }

    suspend fun saveStorageUsage(usage: StorageUsage) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STORAGE_HAS_CACHE] = true
            prefs[KEY_STORAGE_TOTAL_BYTES] = usage.totalBytes
            prefs[KEY_STORAGE_PHOTO_BYTES] = usage.photoBytes
            prefs[KEY_STORAGE_VIDEO_BYTES] = usage.videoBytes
            prefs[KEY_STORAGE_OTHER_BYTES] = usage.otherBytes
            prefs[KEY_STORAGE_PHOTO_COUNT] = usage.photoCount
            prefs[KEY_STORAGE_VIDEO_COUNT] = usage.videoCount
            prefs[KEY_STORAGE_OTHER_COUNT] = usage.otherCount
            prefs[KEY_STORAGE_TOTAL_COUNT] = usage.totalObjectCount
            prefs[KEY_STORAGE_LAST_UPDATED] = usage.lastUpdated
        }
    }

    suspend fun updateStorageUsageIncrement(
        bytesDelta: Long,
        category: MediaCategory,
        countDelta: Int = 1
    ) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_STORAGE_HAS_CACHE] == true) {
                var photoBytes = prefs[KEY_STORAGE_PHOTO_BYTES] ?: 0L
                var videoBytes = prefs[KEY_STORAGE_VIDEO_BYTES] ?: 0L
                var otherBytes = prefs[KEY_STORAGE_OTHER_BYTES] ?: 0L
                var photoCount = prefs[KEY_STORAGE_PHOTO_COUNT] ?: 0
                var videoCount = prefs[KEY_STORAGE_VIDEO_COUNT] ?: 0
                var otherCount = prefs[KEY_STORAGE_OTHER_COUNT] ?: 0

                when (category) {
                    MediaCategory.PHOTO -> {
                        photoBytes = (photoBytes + bytesDelta).coerceAtLeast(0L)
                        photoCount = (photoCount + countDelta).coerceAtLeast(0)
                    }
                    MediaCategory.VIDEO -> {
                        videoBytes = (videoBytes + bytesDelta).coerceAtLeast(0L)
                        videoCount = (videoCount + countDelta).coerceAtLeast(0)
                    }
                    MediaCategory.OTHER -> {
                        otherBytes = (otherBytes + bytesDelta).coerceAtLeast(0L)
                        otherCount = (otherCount + countDelta).coerceAtLeast(0)
                    }
                }
                prefs[KEY_STORAGE_PHOTO_BYTES] = photoBytes
                prefs[KEY_STORAGE_VIDEO_BYTES] = videoBytes
                prefs[KEY_STORAGE_OTHER_BYTES] = otherBytes
                prefs[KEY_STORAGE_TOTAL_BYTES] = photoBytes + videoBytes + otherBytes
                prefs[KEY_STORAGE_PHOTO_COUNT] = photoCount
                prefs[KEY_STORAGE_VIDEO_COUNT] = videoCount
                prefs[KEY_STORAGE_OTHER_COUNT] = otherCount
                prefs[KEY_STORAGE_TOTAL_COUNT] = photoCount + videoCount + otherCount
                prefs[KEY_STORAGE_LAST_UPDATED] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clearStorageUsageCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_STORAGE_HAS_CACHE)
            prefs.remove(KEY_STORAGE_TOTAL_BYTES)
            prefs.remove(KEY_STORAGE_PHOTO_BYTES)
            prefs.remove(KEY_STORAGE_VIDEO_BYTES)
            prefs.remove(KEY_STORAGE_OTHER_BYTES)
            prefs.remove(KEY_STORAGE_PHOTO_COUNT)
            prefs.remove(KEY_STORAGE_VIDEO_COUNT)
            prefs.remove(KEY_STORAGE_OTHER_COUNT)
            prefs.remove(KEY_STORAGE_TOTAL_COUNT)
            prefs.remove(KEY_STORAGE_LAST_UPDATED)
        }
    }

    val aiProviderModeFlow: Flow<com.example.ai.model.AiProviderType> = context.dataStore.data.map { prefs ->
        try {
            val modeStr = prefs[KEY_AI_PROVIDER_MODE] ?: com.example.ai.model.AiProviderType.ON_DEVICE.name
            com.example.ai.model.AiProviderType.valueOf(modeStr)
        } catch (_: Exception) {
            com.example.ai.model.AiProviderType.ON_DEVICE
        }
    }

    val aiGeminiApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val encryptedKey = prefs[KEY_AI_GEMINI_API_KEY] ?: ""
        if (encryptedKey.isNotEmpty()) {
            keystoreManager.decrypt(encryptedKey)
        } else {
            ""
        }
    }

    suspend fun setAiProviderMode(mode: com.example.ai.model.AiProviderType) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDER_MODE] = mode.name
        }
    }

    suspend fun setAiGeminiApiKey(apiKey: String) {
        val encryptedKey = if (apiKey.isNotBlank()) {
            keystoreManager.encrypt(apiKey.trim())
        } else {
            ""
        }
        context.dataStore.edit { prefs ->
            if (encryptedKey.isNotEmpty()) {
                prefs[KEY_AI_GEMINI_API_KEY] = encryptedKey
            } else {
                prefs.remove(KEY_AI_GEMINI_API_KEY)
            }
        }
    }

    suspend fun clearAiGeminiApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_AI_GEMINI_API_KEY)
        }
    }

    // Smart Collections Flows & Setters
    val smartCollectionsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_ENABLED] ?: true
    }

    val smartCollectionsAutoAnalyzeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_AUTO_ANALYZE] ?: true
    }

    val smartCollectionsAnalyzeVideosFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_ANALYZE_VIDEOS] ?: true
    }

    val smartCollectionsWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_WIFI_ONLY] ?: false
    }

    val smartCollectionsRequireChargingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_REQUIRE_CHARGING] ?: false
    }

    val smartCollectionsModelInstalledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_MODEL_INSTALLED] ?: false
    }

    val smartCollectionsModelVersionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_COLLECTIONS_MODEL_VERSION] ?: "v1.0"
    }

    suspend fun setSmartCollectionsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_ENABLED] = enabled
        }
    }

    suspend fun setSmartCollectionsAutoAnalyze(autoAnalyze: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_AUTO_ANALYZE] = autoAnalyze
        }
    }

    suspend fun setSmartCollectionsAnalyzeVideos(analyzeVideos: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_ANALYZE_VIDEOS] = analyzeVideos
        }
    }

    suspend fun setSmartCollectionsWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setSmartCollectionsRequireCharging(requireCharging: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_REQUIRE_CHARGING] = requireCharging
        }
    }

    suspend fun setSmartCollectionsModelInstalled(installed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_MODEL_INSTALLED] = installed
        }
    }

    suspend fun setSmartCollectionsModelVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMART_COLLECTIONS_MODEL_VERSION] = version
        }
    }
}
