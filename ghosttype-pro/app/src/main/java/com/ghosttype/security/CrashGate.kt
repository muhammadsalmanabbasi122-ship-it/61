package com.ghosttype.security

import android.content.Context
import com.ghosttype.BuildConfig
import com.ghosttype.utils.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches a separate Pastebin URL that only carries the crash_versions
 * kill-switch list. Runs synchronously so GhostTypeApp.onCreate() can
 * kill the process before any UI renders.
 */
internal object CrashGate {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun check(ctx: Context): Boolean {
        val prefs = SettingsStore.prefs(ctx)
        // If already flagged, skip network — will crash anyway.
        if (prefs.getBoolean("crash_app_triggered", false)) return true

        val urlStr = Obf.decode(ctx, ObfConstants.CRASH_URL)
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
                val versions = root.optJSONArray("crash_versions") ?: return false
                val currentVer = BuildConfig.VERSION_NAME
                var hit = false
                for (i in 0 until versions.length()) {
                    if (currentVer == versions.optString(i, "")) {
                        hit = true
                        break
                    }
                }
                if (hit) {
                    prefs.edit().putBoolean("crash_app_triggered", true).apply()
                } else {
                    // Version removed from list → un-crash
                    prefs.edit().remove("crash_app_triggered").apply()
                }
                hit
            }
        } catch (_: Exception) {
            false
        }
    }
}
