package com.example.data.kobo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the KoboToolbox Data API configuration (server URL, project/asset UID, API token).
 *
 * The API token is a secret, so this is backed by [EncryptedSharedPreferences] (AndroidX
 * Security) rather than the plain SharedPreferences used elsewhere in the app. Values are
 * encrypted at rest with a key held in the Android Keystore, and the token is never logged.
 *
 * If secure storage can't be initialized for some reason (e.g. a corrupted keystore entry
 * after a device restore), we fall back to a private, app-only SharedPreferences file rather
 * than crashing the app — the field worker can still use the app, just re-enter the token.
 */
class KoboSecureSettings(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kobo_data_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w("KoboSecureSettings", "Falling back to standard SharedPreferences: ${e.javaClass.simpleName}")
        context.getSharedPreferences("kobo_data_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _assetUid = MutableStateFlow(prefs.getString(KEY_ASSET_UID, "") ?: "")
    val assetUid: StateFlow<String> = _assetUid.asStateFlow()

    private val _apiToken = MutableStateFlow(prefs.getString(KEY_API_TOKEN, "") ?: "")
    val apiToken: StateFlow<String> = _apiToken.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    val isConfigured: Boolean
        get() = _serverUrl.value.isNotBlank() && _assetUid.value.isNotBlank() && _apiToken.value.isNotBlank()

    fun saveConfig(serverUrl: String, assetUid: String, apiToken: String) {
        val cleanServer = serverUrl.trim().trimEnd('/')
        val cleanUid = assetUid.trim()
        val cleanToken = apiToken.trim()
        prefs.edit {
            putString(KEY_SERVER_URL, cleanServer)
            putString(KEY_ASSET_UID, cleanUid)
            putString(KEY_API_TOKEN, cleanToken)
        }
        _serverUrl.value = cleanServer
        _assetUid.value = cleanUid
        _apiToken.value = cleanToken
    }

    fun clearConfig() {
        prefs.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_ASSET_UID)
            remove(KEY_API_TOKEN)
            remove(KEY_LAST_SYNC)
        }
        _serverUrl.value = DEFAULT_SERVER_URL
        _assetUid.value = ""
        _apiToken.value = ""
        _lastSyncTime.value = 0L
    }

    fun updateLastSyncTime(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC, timestamp) }
        _lastSyncTime.value = timestamp
    }

    companion object {
        private const val KEY_SERVER_URL = "kobo_data_server_url"
        private const val KEY_ASSET_UID = "kobo_data_asset_uid"
        private const val KEY_API_TOKEN = "kobo_data_api_token"
        private const val KEY_LAST_SYNC = "kobo_data_last_sync"
        const val DEFAULT_SERVER_URL = "https://kf.kobotoolbox.org"
    }
}
