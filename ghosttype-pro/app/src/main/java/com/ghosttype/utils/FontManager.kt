package com.ghosttype.utils

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FontEntry(val name: String, val path: String, val builtIn: Boolean = false)

object FontManager {

    private const val TAG = "FontManager"

    /**
     * Robust loader for custom imported .ttf / .otf files.
     *
     * Why this is more than `Typeface.createFromFile(...)`:
     *
     *   1.  On many OEM ROMs (Xiaomi MIUI, Vivo / Oppo ColorOS, some Samsung
     *       builds) `createFromFile` will silently return `Typeface.DEFAULT`
     *       for perfectly valid .ttf files that contain "exotic" tables
     *       (DSIG, GSUB extensions, variable-font axes). The user just sees
     *       Roboto and assumes their custom font "didn't work".
     *
     *   2.  `Typeface.Builder` (API 26+) uses Android's modern Minikin
     *       loader which accepts a much broader set of font formats and
     *       reports failure cleanly via `build()` returning null instead of
     *       a stub typeface.
     *
     *   3.  Even when both APIs "succeed" they sometimes hand back the
     *       system default. We detect that case (ref-equality with
     *       `Typeface.DEFAULT`) and surface it to the caller as `null` so
     *       the picker can show a real error toast instead of pretending
     *       the font applied.
     *
     * Returns the loaded typeface, or `null` if the font genuinely couldn't
     * be decoded. Callers should fall back to `Typeface.DEFAULT` themselves.
     */
    internal fun loadCustomTypeface(path: String): Typeface? {
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() < 4L) {
            Log.e(TAG, "loadCustomTypeface: file missing/unreadable: $path " +
                "(exists=${file.exists()}, canRead=${file.canRead()}, len=${file.length()})")
            return null
        }
        // 1) Modern path — Typeface.Builder (API 26+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val tf = Typeface.Builder(file).build()
                if (tf != null && tf !== Typeface.DEFAULT) {
                    Log.i(TAG, "loadCustomTypeface: Builder OK → $path")
                    return tf
                }
                Log.w(TAG, "loadCustomTypeface: Builder returned null/DEFAULT for $path")
            } catch (t: Throwable) {
                Log.w(TAG, "loadCustomTypeface: Builder threw for $path", t)
            }
        }
        // 2) Legacy path — createFromFile.
        try {
            val tf = Typeface.createFromFile(file)
            if (tf !== Typeface.DEFAULT) {
                Log.i(TAG, "loadCustomTypeface: createFromFile OK → $path")
                return tf
            }
            Log.e(TAG, "loadCustomTypeface: createFromFile returned DEFAULT for $path")
        } catch (t: Throwable) {
            Log.e(TAG, "loadCustomTypeface: createFromFile threw for $path", t)
        }
        return null
    }

    /**
     * Same as [loadCustomTypeface] but shows a toast when the load fails
     * so the end-user knows their font is invalid instead of silently
     * seeing Roboto.
     */
    fun loadCustomTypefaceOrWarn(ctx: Context, path: String): Typeface {
        val tf = loadCustomTypeface(path)
        if (tf != null) return tf
        try {
            Toast.makeText(
                ctx,
                "Couldn't load this font file. The .ttf may be unsupported or corrupted.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Throwable) { /* ctx may not be UI-attached */ }
        return Typeface.DEFAULT
    }

    /** Custom imported .ttf/.otf fonts only. */
    fun list(ctx: Context): List<FontEntry> {
        val raw = SettingsStore.prefs(ctx).getString(SettingsStore.KEY_FONTS_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                FontEntry(o.optString("name"), o.optString("path"))
            }.filter { File(it.path).exists() }
        } catch (e: Exception) { emptyList() }
    }

    /** Built-in fonts (real bundled .ttf assets) — always available. */
    fun builtIn(): List<FontEntry> =
        BuiltInFonts.ALL.map { FontEntry(it.name, it.path, builtIn = true) }

    /** Combined list shown in the on-keyboard font picker: built-in first, then custom. */
    fun all(ctx: Context): List<FontEntry> = builtIn() + list(ctx)

    private fun saveList(ctx: Context, list: List<FontEntry>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().put("name", f.name).put("path", f.path))
        }
        SettingsStore.prefs(ctx).edit().putString(SettingsStore.KEY_FONTS_LIST, arr.toString()).apply()
    }

    fun activePath(ctx: Context): String? {
        val raw = SettingsStore.prefs(ctx).getString(SettingsStore.KEY_FONT_PATH, null)
        if (raw.isNullOrBlank()) return null
        // Built-in pseudo-paths (asset: or legacy builtin:) are always considered "available".
        if (BuiltInFonts.isBuiltIn(raw)) return raw
        return raw.takeIf { File(it).exists() }
    }

    fun setActive(ctx: Context, path: String?) {
        // Wipe the typeface cache BEFORE writing the pref so the
        // SharedPreferences listener (which reloads the keyboard view)
        // can never read a stale cached typeface for the new path.
        invalidateCache()
        SettingsStore.prefs(ctx).edit().putString(SettingsStore.KEY_FONT_PATH, path ?: "").apply()
    }

    fun importFont(ctx: Context, src: java.io.InputStream, fileName: String): FontEntry {
        val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
        // Avoid filename collisions
        val safeBase = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var out = File(dir, safeBase)
        var n = 1
        while (out.exists()) { out = File(dir, "${n}_$safeBase"); n++ }
        src.use { input -> out.outputStream().use { input.copyTo(it) } }

        // Validate the file is actually a usable font BEFORE we list/activate it.
        // If the .ttf can't be decoded by Android's font loader, deleting the
        // copy now prevents a "ghost" entry that silently shows Roboto.
        val testTf = loadCustomTypeface(out.absolutePath)
        if (testTf == null) {
            Log.e(TAG, "importFont: file copied but Android can't load it: ${out.absolutePath}")
            try { out.delete() } catch (_: Throwable) {}
            try {
                Toast.makeText(
                    ctx,
                    "Couldn't load \"$fileName\". The .ttf may be unsupported (variable font, signed, etc).",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Throwable) {}
            // Return a sentinel entry pointing at the (now-deleted) file so
            // callers don't crash. It won't appear in `list(ctx)` because
            // the file no longer exists.
            return FontEntry(safeBase, out.absolutePath)
        }

        val entry = FontEntry(safeBase, out.absolutePath)
        val updated = list(ctx).toMutableList().also { it.add(entry) }
        saveList(ctx, updated)
        setActive(ctx, out.absolutePath)
        try {
            Toast.makeText(ctx, "Font \"$fileName\" loaded and applied.", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
        return entry
    }

    fun delete(ctx: Context, path: String) {
        File(path).takeIf { it.exists() }?.delete()
        val updated = list(ctx).filterNot { it.path == path }
        saveList(ctx, updated)
        if (activePath(ctx) == path) setActive(ctx, null)
    }

    /** Memo cache for the currently-rendered keyboard typeface keyed by path+style. */
    private val keyTfCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    fun loadKeyTypeface(ctx: Context): Typeface {
        val path = activePath(ctx)
        val bold = SettingsStore.prefs(ctx).getBoolean(SettingsStore.KEY_FONT_BOLD, false)
        val italic = SettingsStore.prefs(ctx).getBoolean(SettingsStore.KEY_FONT_ITALIC, false)
        val userStyle = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val cacheKey = "${path ?: ""}|$userStyle"
        keyTfCache[cacheKey]?.let { return it }

        val base: Typeface = when {
            // Bundled asset (real .ttf shipped with the app)
            BuiltInFonts.isBuiltIn(path) -> {
                val bf = BuiltInFonts.byPath(path) ?: return Typeface.DEFAULT
                val asset = BuiltInFonts.typefaceFor(ctx, bf)
                // Apply intrinsic style of the entry (e.g. "Bold Italic"). If the
                // user *also* toggled bold/italic in the picker we OR the styles
                // together so the overlay sticks.
                val combined = bf.style or userStyle
                if (combined == Typeface.NORMAL) asset
                else try { Typeface.create(asset, combined) } catch (_: Throwable) { asset }
            }
            // Custom imported .ttf — uses the robust loader so OEM ROMs
            // that silently fall back on `createFromFile` still work via
            // the modern `Typeface.Builder` path.
            !path.isNullOrBlank() && File(path).exists() -> {
                val custom = loadCustomTypeface(path) ?: Typeface.DEFAULT
                if (userStyle == Typeface.NORMAL) custom
                else try { Typeface.create(custom, userStyle) } catch (_: Throwable) { custom }
            }
            // No active font selected → system default
            else -> if (userStyle == Typeface.NORMAL) Typeface.DEFAULT
                    else try { Typeface.create(Typeface.DEFAULT, userStyle) } catch (_: Throwable) { Typeface.DEFAULT }
        }
        keyTfCache[cacheKey] = base
        return base
    }

    /** Wipes the cached keyboard typeface so the next reload picks up the new font. */
    fun invalidateCache() { keyTfCache.clear() }
}
