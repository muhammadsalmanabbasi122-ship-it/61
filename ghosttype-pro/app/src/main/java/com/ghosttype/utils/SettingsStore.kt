package com.ghosttype.utils

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private const val NAME = "ghosttype_settings"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Theme
    const val KEY_THEME = "theme_id"
    const val KEY_KEY_BG = "color_key_bg"
    const val KEY_KEY_TEXT = "color_key_text"
    const val KEY_KB_BG = "color_kb_bg"
    const val KEY_SUGG_BG = "color_sugg_bg"
    const val KEY_PRESSED = "color_pressed"
    const val KEY_BORDER_STYLE = "border_style"
    const val KEY_KEY_OPACITY = "key_opacity"
    const val KEY_BG_IMAGE_URI = "bg_image_uri"
    const val KEY_BG_IMAGE_OPACITY = "bg_image_opacity"
    const val KEY_BG_SHOW_BORDERS = "bg_show_borders"   // true = show key boxes over bg image, false = transparent
    // When true, the same custom bg image is also applied as the background
    // of every individual key (each key shows the corresponding portion of
    // the keyboard image). User-requested on/off toggle in Theme settings.
    const val KEY_BG_IMAGE_ON_KEYS = "bg_image_on_keys"

    // Keyboard
    const val KEY_HAPTIC = "haptic_enabled"
    const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
    const val KEY_SOUND = "sound_enabled"
    const val KEY_SOUND_VOLUME = "sound_volume"
    const val KEY_KB_HEIGHT_PCT = "kb_height_pct"
    const val KEY_KEY_HEIGHT_DP = "key_height_dp"
    // v1.7 — per-key spacing slider. Default 3 dp gives a tight Gboard-like
    // packed look; user can dial up to 8 dp for more separation. Replaces
    // the hard-coded 6 dp from v1.6 which was making keys look small.
    const val KEY_KEY_MARGIN_DP = "key_margin_dp"
    // v1.7 — toggle baseline 3D shadow under each key (Gboard-style).
    // ON = soft elevation drop-shadow at rest, key "presses in" on tap.
    // OFF = flat keys (slightly faster, matches the v1.5 look).
    const val KEY_KEY_3D_SHADOW = "key_3d_shadow"
    const val KEY_KEY_TEXT_SIZE = "key_text_size"
    const val KEY_FONT_PATH = "custom_font_path"
    const val KEY_FONT_BOLD = "font_bold"
    const val KEY_FONT_ITALIC = "font_italic"
    const val KEY_FONTS_LIST = "fonts_list_json"
    // Unicode style applied to every typed character so the OUTPUT in chat
    // apps actually changes ("Aa" picker). TTF fonts only restyle the keys
    // on GhostType's own keyboard — they cannot affect text inside other
    // apps. This is the real fix for "fancy text in any chat app".
    const val KEY_FONT_STYLE = "font_unicode_style"   // id from UnicodeFonts.STYLES, e.g. "normal", "bold_script"
    // Layout order — when true, suggestion row sits ABOVE the toolbar
    // (Gboard-style). Default true; user can flip in Theme settings.
    const val KEY_SUGGESTIONS_ON_TOP = "suggestions_on_top"
    const val KEY_LANGUAGE = "current_language"
    // When true, the Enter key ALWAYS inserts \n (legacy behavior).
    // When false (NEW default), Enter is "smart":
    //   - Multi-line / IME_ACTION_NONE / IME_ACTION_UNSPECIFIED  → newline
    //     (chat apps like WhatsApp / Messenger / Telegram fall here)
    //   - Otherwise (browser address bar, search box, single-line "Done"
    //     fields, etc.) → fire the field's IME action (real Enter press).
    //   - LONG-PRESS on Enter → always inserts a literal \n no matter what.
    const val KEY_ENTER_NEWLINE = "enter_always_newline"

    // Auto-Type
    const val KEY_AT_DELAY = "autotype_delay"
    const val KEY_AT_LOOP = "autotype_loop"
    const val KEY_AT_SEND_MODE = "autotype_send_mode"  // direct | paste
    const val KEY_AT_LAST_FILE = "autotype_last_file"
    const val KEY_AT_LAST_FILE_NAME = "autotype_last_file_name"
    const val KEY_AT_START_LINE = "autotype_start_line"
    const val KEY_AT_AUTO_SEND = "autotype_auto_send"
    const val KEY_AT_CUSTOM_TEXT = "autotype_custom_text"
    const val KEY_AT_SEND_DELAY_MS = "autotype_send_delay_ms"   // delay between commit and send (default 300)
    const val KEY_AT_SEND_METHOD = "autotype_send_method"       // auto | ime | accessibility | pointer
    const val KEY_AT_CHAR_DELAY_MS = "autotype_char_delay_ms"   // ms between each typed character in DIRECT mode (default 35)
    // v1.10 — optional "Target name" prefix written before every auto-typed
    // line. When non-blank, AutoTypeEngine.start() prepends "$targetName "
    // (with a single trailing space) to each line. Blank / unset = no
    // prefix, behaviour matches earlier builds. Use case: addressing each
    // chat reply to a specific person, e.g. setting target=CHAND turns the
    // line "kaisa hai" into "CHAND kaisa hai".
    const val KEY_AT_TARGET_NAME = "autotype_target_name"

    // Pointer overlay
    const val KEY_POINTER_ENABLED = "pointer_enabled"
    const val KEY_POINTER_X = "pointer_x"
    const val KEY_POINTER_Y = "pointer_y"
    const val KEY_POINTER_LOCKED = "pointer_locked"         // when true, dot ignores touches (just visual)
    const val KEY_POINTER_MULTI_CLICK = "pointer_multi_click"  // when true, pointer clicks multiple times per send
    const val KEY_POINTER_SIZE_DP = "pointer_size_dp"       // dot diameter in dp (default 28)
    const val KEY_POINTER_CLICK_DELAY_MS = "pointer_click_delay_ms" // extra wait (ms) before pointer fires click (default 0)
    const val KEY_POINTER_AUTO_CLICK_INTERVAL_MS = "pointer_auto_click_interval_ms" // auto-click interval in ms (default 1000)

    const val KEY_UI_THEME = "ui_theme"  // "dark" or "light"

    // Suggestions / Auto-save sentence
    const val KEY_SUGGESTIONS = "suggestions_enabled"
    const val KEY_AUTOCORRECT = "autocorrect_enabled"
    const val KEY_AUTO_SAVE_SENTENCE = "auto_save_sentence"
    const val KEY_AUTO_SAVE_MIN_LEN = "auto_save_min_len"
    // Auto-capitalize the first letter of each sentence: ON when the cursor
    // is at the start of an empty field, right after a sentence terminator
    // (`.` `?` `!` `۔`) followed by space, or right after a newline. Default
    // ON — matches Gboard / SwiftKey / iOS behaviour the user expects.
    const val KEY_AUTO_CAPS = "auto_capitalize"

    // Caps mode — when true, all typed text is uppercased
    const val KEY_CAPS_MODE = "caps_mode"

    // Math mode — auto-convert letters to math-style numbers while Auto-Typing
    const val KEY_MATH_ENABLED = "math_enabled"
    const val KEY_MATH_COUNT   = "math_count"   // how many times each line is typed

    // FYT — Fancy Repeat Typing
    // When ON, every character in a line is repeated KEY_FYT_COUNT times
    // before it is typed.  e.g. "hello" + count 3 → "hhheeelllooo"
    const val KEY_FYT_ENABLED = "fyt_enabled"
    const val KEY_FYT_COUNT   = "fyt_count"
    const val KEY_FYT_WORDS   = "fyt_words"

    // Clipboard
    const val KEY_CLIP_AUTO_DELETE_DAYS = "clip_auto_delete_days"

    // Emoji
    const val KEY_EMOJI_RECENT = "emoji_recent"

    // v1.10 — first-run defaults flag. Bumping the suffix forces a re-apply
    // of the curated default theme + sizing settings (pastel-blue background,
    // 71% key opacity, 56dp keys, 1dp spacing, 3D shadow, etc.) for users
    // who installed older builds. Once applied we set this to true so we
    // never overwrite the user's customisations later.
    // Bumped suffix → "_b" so users who already ran the first v1.10 build
    // (which defaulted to the dark theme) get the new Rose Gold default
    // re-applied on next launch. Re-applying the picture/keys values that
    // were already correct is a harmless no-op. Bump again whenever the
    // curated default set in GhostTypeApp.applyDefaultsOnFirstRun changes.
    const val KEY_DEFAULTS_APPLIED = "defaults_v10_applied_b"

    // Tasks popup — set true once user completes both follow tasks
    const val KEY_TASKS_UNLOCKED = "tasks_unlocked_v1"

    // Plans screen — saved name + active plan
    const val KEY_PLANS_USER_NAME       = "plans_user_name"
    const val KEY_ACTIVE_PLAN_NAME      = "active_plan_name"      // e.g. "Quarterly"
    const val KEY_ACTIVE_PLAN_PRICE     = "active_plan_price"     // e.g. "$0.50"
    const val KEY_ACTIVE_PLAN_DURATION  = "active_plan_duration"  // e.g. "3 Months"
    const val KEY_PLAN_STARTED_MS       = "plan_started_ms"       // Unix ms when plan was activated (0 = not set)
    const val KEY_PLAN_EXPIRY_MS        = "plan_expiry_ms"        // Unix ms when plan expires (0 = Lifetime / not set)
    const val KEY_GITHUB_APPROVED_PLAN  = "github_approved_plan"  // Plan name set by CHAND in GitHub JSON (blank = not set)
    const val KEY_REMOTE_APP_VERSION    = "remote_app_version"    // Latest version string from GitHub JSON (blank = not fetched yet)
    const val KEY_DOWNLOAD_URL          = "download_url"          // APK download URL from GitHub JSON (blank = not set)
    const val KEY_UPDATE_SHOW_VERSION   = "update_show_version"   // Version number to display in ForceUpdate popup (blank = hide)
    const val KEY_DEVICE_RESET_TOKEN    = "device_reset_token"    // Random token that changes device ID on plan expiry

    fun getDeviceResetToken(ctx: Context): String {
        return prefs(ctx).getString(KEY_DEVICE_RESET_TOKEN, "") ?: ""
    }

    fun resetDeviceToken(ctx: Context) {
        val token = java.util.UUID.randomUUID().toString().take(8)
        prefs(ctx).edit().putString(KEY_DEVICE_RESET_TOKEN, token).apply()
    }

    /**
     * Returns true if the user has an active (non-expired) plan or a lifetime plan.
     * Returns false if no plan is set, or if the plan has expired.
     */
    fun isPlanActive(ctx: Context): Boolean {
        val p = prefs(ctx)
        val planName = p.getString(KEY_ACTIVE_PLAN_NAME, "") ?: ""
        if (planName.isEmpty()) return false
        if (planName == "Lifetime") return true
        val expiry = p.getLong(KEY_PLAN_EXPIRY_MS, 0L)
        return expiry > 0L && System.currentTimeMillis() < expiry
    }

    /**
     * Returns true if a plan exists but has expired (trial or paid).
     */
    fun isPlanExpired(ctx: Context): Boolean {
        val p = prefs(ctx)
        val planName = p.getString(KEY_ACTIVE_PLAN_NAME, "") ?: ""
        if (planName.isEmpty()) return false
        if (planName == "Lifetime") return false
        val expiry = p.getLong(KEY_PLAN_EXPIRY_MS, 0L)
        return expiry > 0L && System.currentTimeMillis() >= expiry
    }

    /**
     * Wipes every key in SharedPreferences — equivalent to a factory reset
     * for all app settings. After calling this, GhostTypeApp.applyDefaultsOnFirstRun
     * will re-apply the curated defaults on the very next keyboard open because
     * KEY_DEFAULTS_APPLIED is gone too.
     *
     * Does NOT delete the Room clipboard DB — call resetClipboard() for that.
     */
    fun resetAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
