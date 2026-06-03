package com.ghosttype

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GhostTypeApp : Application() {
    private var clipboardWatcher: com.ghosttype.utils.ClipboardWatcher? = null

    override fun onCreate() {
        super.onCreate()
        // Fetch crash URL first so the crash_app_triggered flag is
        // set/cleared before we check it below.
        runCatching { com.ghosttype.security.CrashGate.check(this) }
        if (com.ghosttype.utils.SettingsStore.prefs(this)
                .getBoolean("crash_app_triggered", false)) {
            throw RuntimeException("This app has been remotely disabled by the developer.")
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    append("===== GhostType Pro Crash =====\n")
                    append("Time: ").append(ts).append("\n")
                    append("Thread: ").append(t.name).append("\n")
                    append("Device: ").append(android.os.Build.MANUFACTURER).append(" ")
                        .append(android.os.Build.MODEL).append(" / Android ")
                        .append(android.os.Build.VERSION.RELEASE).append(" (SDK ")
                        .append(android.os.Build.VERSION.SDK_INT).append(")\n")
                    append("App: 1.0\n\n")
                    append(sw.toString())
                }
                Log.e("GhostTypeCrash", text)
                runCatching {
                    val dir = File(filesDir, "crash").apply { mkdirs() }
                    File(dir, "last_crash.txt").writeText(text)
                    File(dir, "crash_${System.currentTimeMillis()}.txt").writeText(text)
                }
                runCatching {
                    val ext = getExternalFilesDir(null)
                    if (ext != null) {
                        File(ext, "GhostType_last_crash.txt").writeText(text)
                    }
                }
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }

        // Kill all stale services from the previous build so the new
        // version starts clean. Without this, GitHub builds stack old
        // processes that hold stale resources (auto-type coroutines,
        // overlay dots, clipboard listeners) and the user sees bugs or
        // crashes. Only fires on version upgrade — not every launch.
        runCatching {
            val p = com.ghosttype.utils.SettingsStore.prefs(this)
            val prev = p.getInt("build_version", 0)
            val cur = com.ghosttype.BuildConfig.VERSION_CODE
            if (cur > prev) {
                com.ghosttype.ime.AutoTypeEngine.stop()
                com.ghosttype.ime.FloatingPointerService.stop(this)
                com.ghosttype.ime.AutoTypeForegroundService.stop(this)
                clipboardWatcher?.stop()
                p.edit().putInt("build_version", cur).apply()
            }
        }

        // Auto-restart pointer overlay if it was enabled and overlay permission is granted.
        runCatching {
            val prefs = com.ghosttype.utils.SettingsStore.prefs(this)
            val enabled = prefs.getBoolean(com.ghosttype.utils.SettingsStore.KEY_POINTER_ENABLED, false)
            if (enabled && android.provider.Settings.canDrawOverlays(this)) {
                com.ghosttype.ime.FloatingPointerService.start(this)
            }
        }

        // v1.10 — first-run default theme + sizing. Bundled pastel-blue
        // background image + Gboard-style sizing get applied ONCE on first
        // launch (or first launch after upgrading from a build that didn't
        // ship them). Once applied, the flag prevents us from ever
        // overwriting the user's later customisations.
        runCatching { applyDefaultsOnFirstRun() }

        // Always-on clipboard history capture. The Application stays alive while
        // ANY of our components (IME, accessibility service, foreground service,
        // activity) is running, which together keep the listener active much longer
        // than the previous IME-only setup. Items are persisted in Room so they
        // survive power off / power on.
        runCatching {
            clipboardWatcher = com.ghosttype.utils.ClipboardWatcher(this).also { it.start() }
        }

        // Schedule the background periodic approval re-check (every 15 min).
        // Also enqueue an immediate one-time check so a key removal from
        // GitHub is detected within seconds of the next app/keyboard start,
        // rather than waiting up to 15 min for the periodic worker to fire.
        runCatching {
            com.ghosttype.security.ApprovalRefreshWorker.schedule(this)
            com.ghosttype.security.ApprovalRefreshWorker.checkNow(this)
        }

        // Initialize AdMob (non-blocking, done on a background thread).
        runCatching {
            com.google.android.gms.ads.MobileAds.initialize(this)
        }
    }

    /**
     * v1.10 — Curated default keyboard look. Runs ONCE per install (gated by
     * [com.ghosttype.utils.SettingsStore.KEY_DEFAULTS_APPLIED]) so it never
     * stomps on settings the user has tuned themselves later.
     *
     * Defaults applied:
     *   • Background image  → bundled pastel-blue PNG (drawable-nodpi)
     *   • Background opacity → 100 %
     *   • Show key boxes over background → ON
     *   • Apply background image to keys → ON
     *   • Border style       → rounded
     *   • Key opacity        → 71 %
     *   • Key text size      → 18 sp
     *   • Key / row height   → 56 dp
     *   • Key spacing        → 1 dp
     *   • 3D key shadow      → ON
     */
    private fun applyDefaultsOnFirstRun() {
        val prefs = com.ghosttype.utils.SettingsStore.prefs(this)
        val DEFAULTS_KEY = "defaults_v13_applied"
        if (prefs.getBoolean(DEFAULTS_KEY, false)) return
        // Apply cute_pill_blue colors as per-color prefs so ThemeManager.current()
        // reads the right values even before the user opens Themes.
        com.ghosttype.utils.ThemeManager.setTheme(this, "dark_modern")
        prefs.edit()
            // Dark Modern: sleek charcoal keyboard with purple accent keys,
            // bright white text — premium dark look with gradient accent glow.
            .putString(com.ghosttype.utils.SettingsStore.KEY_THEME, "dark_modern")
            .putString(com.ghosttype.utils.SettingsStore.KEY_BG_IMAGE_URI, "")
            .putInt(com.ghosttype.utils.SettingsStore.KEY_BG_IMAGE_OPACITY, 100)
            .putBoolean(com.ghosttype.utils.SettingsStore.KEY_BG_SHOW_BORDERS, false)
            .putBoolean(com.ghosttype.utils.SettingsStore.KEY_BG_IMAGE_ON_KEYS, false)
            .putString(com.ghosttype.utils.SettingsStore.KEY_BORDER_STYLE, "rounded")
            .putInt(com.ghosttype.utils.SettingsStore.KEY_KEY_OPACITY, 100)
            .putInt(com.ghosttype.utils.SettingsStore.KEY_KEY_TEXT_SIZE, 18)
            .putInt(com.ghosttype.utils.SettingsStore.KEY_KEY_HEIGHT_DP, 58)
            .putInt(com.ghosttype.utils.SettingsStore.KEY_KEY_MARGIN_DP, 3)
            .putBoolean(com.ghosttype.utils.SettingsStore.KEY_KEY_3D_SHADOW, true)
            .putBoolean(DEFAULTS_KEY, true)
            .apply()
    }
}
