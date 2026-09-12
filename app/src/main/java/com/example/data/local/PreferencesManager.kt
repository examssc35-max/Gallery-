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
    }
}
