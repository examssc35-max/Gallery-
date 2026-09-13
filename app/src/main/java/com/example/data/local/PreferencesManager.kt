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
    val backupPhotos: Boolean = false,
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

class PreferencesManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager = KeystoreManager()
) {
    companion object {
        val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

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

    val backupSettingsFlow: Flow<BackupSettings> = context.dataStore.data.map { prefs ->
        BackupSettings(
            isAutoBackupEnabled = prefs[KEY_AUTO_BACKUP_ENABLED] ?: false,
            backupVideos = prefs[KEY_BACKUP_VIDEOS] ?: true,
            backupPhotos = prefs[KEY_BACKUP_PHOTOS] ?: false,
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
}
