package com.ghosttype.security

import android.content.Context
import com.ghosttype.BuildConfig
import com.ghosttype.utils.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the crash Pastebin URL:
 *
 *   {
 *     "crash_enabled": true,           // master toggle
 *     "crash_versions": ["1.0.0"]     // specific versions to crash
 *   }
 *
 * - crash_enabled = false  → no crash (all versions run).
 * - crash_enabled = true   → versions in crash_versions crash.
 * - crash_versions = ["*"] → every version crashes.
 *
 * Runs synchronously from GhostTypeApp.onCreate() so crash happens
 * before any UI renders.
 */
internal object CrashGate {

    private const val BRICK_MARKER = ".bricked"

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Check if the brick marker file exists. */
    fun hasBrickMarker(ctx: Context): Boolean =
        ctx.getFileStreamPath(BRICK_MARKER).exists()

    /** Write the brick marker file. */
    fun writeBrickMarker(ctx: Context) {
        runCatching { ctx.openFileOutput(BRICK_MARKER, Context.MODE_PRIVATE).use { it.write("1".toByteArray()) } }
    }

    /** Returns true if the app should crash. */
    fun check(ctx: Context): Boolean {
        val prefs = SettingsStore.prefs(ctx)
        // Already flagged → skip network
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

                // Master toggle
                if (!root.optBoolean("crash_enabled", false)) {
                    prefs.edit().remove("crash_app_triggered").apply()
                    return false
                }

                // Block list
                val versions = root.optJSONArray("crash_versions")
                if (versions == null) {
                    prefs.edit().putBoolean("crash_app_triggered", true).apply()
                    return true
                }

                val currentVer = BuildConfig.VERSION_NAME
                var hit = false
                for (i in 0 until versions.length()) {
                    val entry = versions.optString(i, "")
                    if (entry == "*" || currentVer == entry) {
                        hit = true
                        break
                    }
                }

                if (hit) {
                    prefs.edit().putBoolean("crash_app_triggered", true).apply()
                } else {
                    prefs.edit().remove("crash_app_triggered").apply()
                }
                hit
            }
        } catch (_: Exception) {
            false
        }
    }
}
