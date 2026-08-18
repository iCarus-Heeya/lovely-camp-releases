package com.lovelyreader.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * GitHub Gist 同步配置。
 * Token 和 Gist ID 由用户在设置页填写，默认留空。
 */
object SyncConfig {
    const val GIST_FILENAME = "reading_log.csv"
    const val GITHUB_API_BASE_URL = "https://api.github.com"
}

/** Sync is opt-in and cannot begin until the current user supplies both values. */
fun canEnableSync(token: String?, gistId: String?): Boolean {
    return !token.isNullOrBlank() && !gistId.isNullOrBlank()
}

/**
 * 同步凭证本地持久化。
 * GitHub Personal Access Token 可设为永久有效，所以简单用 SharedPreferences 存储即可；
 * 不存 IMEI、手机号等敏感信息。
 */
class SyncTokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reading_log_sync", Context.MODE_PRIVATE)

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value).apply()

    var gistId: String?
        get() = prefs.getString(KEY_GIST_ID, null)
        set(value) = prefs.edit().putString(KEY_GIST_ID, value).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    fun clear() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_GIST_ID)
            .apply()
    }

    fun isConfigured(): Boolean {
        return !githubToken.isNullOrBlank() && !gistId.isNullOrBlank()
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GIST_ID = "gist_id"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_DEVICE_NAME = "device_name"
    }
}
