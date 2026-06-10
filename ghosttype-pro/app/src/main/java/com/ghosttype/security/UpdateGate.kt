package com.ghosttype.security

import android.content.Context
import com.ghosttype.utils.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the update Pastebin URL that controls the Force Update screen:
 *
 *   {
 *     "app_enabled": true,     // false → global kill (all versions blocked)
 *     "app_version": "1.0.12", // blank = no force update
 *     "download_url": "..."    // download link shown on update screen
 *   }
 *
 * Runs synchronously from GhostTypeApp.onCreate() and GatedApp so the
 * Force Update / lock screen appears before any UI is rendered.
 */
internal object UpdateGate {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the update config and saves flags into SharedPreferences.
     * Returns true if the app should be globally disabled (app_enabled == false).
     */
    fun check(ctx: Context): Boolean {
        val prefs = SettingsStore.prefs(ctx)

        val urlStr = PastebinSecrets.UPDATE_URL
        if (!urlStr.startsWith("https://")) return false

        return try {
            val req = Request.Builder().url(urlStr)
                .header("Accept", "application/json")
                .header("User-Agent", "GhostTypePro")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string() ?: return false
                val trimmed = body.trimStart()
                if (!trimmed.startsWith("{")) return false
                val root = JSONObject(body)

                val enabled = root.optBoolean("app_enabled", true)
                val remoteVer = root.optString("app_version", "").trim()
                val showVer = root.optString("update_show_version", "").trim()
                val dlUrl = root.optString("download_url", "").trim()

                val edit = prefs.edit()
                if (!enabled) {
                    edit.putBoolean("update_gate_disabled", true)
                } else {
                    edit.remove("update_gate_disabled")
                }
                if (remoteVer.isNotBlank()) {
                    edit.putString(SettingsStore.KEY_REMOTE_APP_VERSION, remoteVer)
                } else {
                    edit.remove(SettingsStore.KEY_REMOTE_APP_VERSION)
                }
                if (showVer.isNotBlank()) {
                    edit.putString(SettingsStore.KEY_UPDATE_SHOW_VERSION, showVer)
                } else {
                    edit.remove(SettingsStore.KEY_UPDATE_SHOW_VERSION)
                }
                if (dlUrl.isNotBlank()) {
                    edit.putString(SettingsStore.KEY_DOWNLOAD_URL, dlUrl)
                } else {
                    edit.remove(SettingsStore.KEY_DOWNLOAD_URL)
                }
                edit.apply()

                !enabled
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Cheap read of the cached remote-version prefs (no network). */
    fun cachedRemoteVersion(ctx: Context): String =
        SettingsStore.prefs(ctx).getString(SettingsStore.KEY_REMOTE_APP_VERSION, "") ?: ""
}
