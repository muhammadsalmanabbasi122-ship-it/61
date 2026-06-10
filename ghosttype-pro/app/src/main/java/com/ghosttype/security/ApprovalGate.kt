package com.ghosttype.security

import android.content.Context
import android.content.SharedPreferences
import com.ghosttype.utils.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the Pastebin-hosted approval list and decides whether the
 * current device is allowed to use GhostType Pro.
 *
 * The URL itself is XOR-encrypted with a key derived from the APK
 * signing cert (see [Obf]) — so a thief can't repackage the app to
 * point at their own approval server, and editing the URL inside the
 * binary makes decryption produce garbage and the fetch fails. Both
 * attack paths land the user on the permanent lock screen.
 *
 * STRICT MODE (no bypass):
 *   - Every check MUST successfully reach the approval server.
 *   - If the URL is removed, returns 404, or is unreachable for ANY
 *     reason, the app immediately blocks — no offline grace period.
 *   - Approved state is cached for [CHECK_INTERVAL_MS] (1 h) only
 *     to avoid hitting Pastebin on every keystroke. After 1 h the
 *     device MUST re-verify online; failure = Blocked instantly.
 *   - Blocked always wins over Approved.
 *
 * State machine:
 *   Approved        — id is on the approved list
 *   Blocked         — id is on the blocked/revoked list, OR server unreachable
 *   NotApproved     — id isn't on either list
 *   GloballyDisabled — "app_enabled": false set by CHAND in the JSON
 *   OfflineUnknown  — (legacy, no longer returned in strict mode)
 */
object ApprovalGate {

    sealed class State {
        object Approved : State()
        object Blocked : State()
        object NotApproved : State()
        /** Global kill-switch — CHAND set "app_enabled": false in the JSON.
         *  Locked for ALL users, no offline grace period. */
        object GloballyDisabled : State()
        data class OfflineUnknown(val reason: String) : State()
    }

    private const val PREFS = "ghosttype_gate"
    private const val K_LAST_CHECK = "last_check_at"
    private const val K_LAST_STATE = "last_state"

    // Strict mode: cache valid for 1 hour only. After that MUST re-verify online.
    private const val CHECK_INTERVAL_MS = 1L * 60L * 60L * 1000L       // 1 h
    // No offline grace: if the server is unreachable after cache expires → Blocked.
    private const val MAX_OFFLINE_MS    = CHECK_INTERVAL_MS             // same as check interval

    @Volatile private var http: OkHttpClient? = null
    private fun http(): OkHttpClient {
        http?.let { return it }
        val c = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        http = c
        return c
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Cheap synchronous read of the last cached gate decision. Used
     *  by the IME service (where blocking on the network is not OK)
     *  to decide between showing the keyboard and showing a lock
     *  view. The actual network refresh happens from MainActivity.
     *
     *  STRICT MODE: cache is only valid within CHECK_INTERVAL_MS (1 h).
     *  After that, treat as Blocked until a fresh online check succeeds. */
    fun cachedState(ctx: Context): State {
        val p = prefs(ctx)
        val name = p.getString(K_LAST_STATE, null)
            ?: return State.Blocked  // never checked = blocked
        // Cache expired → force a Blocked result so the app re-verifies.
        val last = p.getLong(K_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) {
            return State.Blocked
        }
        return when (name) {
            "Approved"         -> State.Approved
            "Blocked"          -> State.Blocked
            "NotApproved"      -> State.NotApproved
            "GloballyDisabled" -> State.GloballyDisabled
            else               -> State.Blocked
        }
    }

    fun isApprovedCached(ctx: Context): Boolean {
        // Strict: only return true if cache is fresh (within 1 h) AND Approved.
        val p = prefs(ctx)
        if (p.getString(K_LAST_STATE, null) != "Approved") return false
        val last = p.getLong(K_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last < CHECK_INTERVAL_MS
    }

    /** Full evaluation. Hits the network unless a fresh cached
     *  Approved exists and `force` is false. */
    suspend fun evaluate(ctx: Context, force: Boolean = false): State =
        withContext(Dispatchers.IO) {
            val deviceId = DeviceId.get(ctx)
            val p = prefs(ctx)
            val now = System.currentTimeMillis()
            val lastCheck = p.getLong(K_LAST_CHECK, 0L)
            val lastStateName = p.getString(K_LAST_STATE, null)

            if (!force && lastStateName == "Approved" && now - lastCheck < CHECK_INTERVAL_MS) {
                return@withContext State.Approved
            }

            val urlStr = PastebinSecrets.approvalUrl(ctx)
            if (!urlStr.startsWith("https://")) {
                // Decryption produced garbage — almost always means the
                // APK was repackaged with a different signing cert.
                return@withContext fallback(p, now, lastStateName, "url_decrypt_failed")
            }

            try {
                val req = Request.Builder().url(urlStr)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", "GhostTypePro")
                    .build()
                http().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext fallback(p, now, lastStateName, "http_${resp.code}")
                    }
                    val body = resp.body?.string()
                        ?: return@withContext fallback(p, now, lastStateName, "empty_body")
                    // Pastebin soft-404 returns HTTP 200 with HTML body — treat
                    // non-JSON responses as fetch failure (Blocked), not NotApproved.
                    val trimmed = body.trimStart()
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        return@withContext fallback(p, now, lastStateName, "not_json")
                    }
                    val (state, githubPlan, githubName) = decide(body, deviceId)
                    val edit = p.edit()
                        .putLong(K_LAST_CHECK, now)
                        .putString(K_LAST_STATE, name(state))
                    // Save (or clear) the CHAND-assigned plan + name from the JSON
                    if (state is State.Approved && githubPlan.isNotBlank()) {
                        edit.putString(SettingsStore.KEY_GITHUB_APPROVED_PLAN, githubPlan)
                        val appPrefs = SettingsStore.prefs(ctx)

                        // Auto-fill user name from GitHub JSON (only if not already set)
                        if (githubName.isNotBlank()) {
                            appPrefs.edit()
                                .putString(SettingsStore.KEY_PLANS_USER_NAME, githubName)
                                .apply()
                        }

                        // Sync GitHub plan to local active plan — CHAND's JSON is authoritative.
                        val currentLocal = appPrefs.getString(SettingsStore.KEY_ACTIVE_PLAN_NAME, "") ?: ""
                        if (!currentLocal.equals(githubPlan, ignoreCase = true)) {
                            val durMs = planDurationMs(githubPlan)
                            val activatedAt = System.currentTimeMillis()
                            appPrefs.edit()
                                .putString(SettingsStore.KEY_ACTIVE_PLAN_NAME, githubPlan)
                                .putLong(SettingsStore.KEY_PLAN_STARTED_MS, activatedAt)
                                .apply()
                            if (durMs < 0) { // Lifetime
                                appPrefs.edit()
                                    .putLong(SettingsStore.KEY_PLAN_EXPIRY_MS, 0L)
                                    .putString(SettingsStore.KEY_ACTIVE_PLAN_DURATION, "Forever")
                                    .putString(SettingsStore.KEY_ACTIVE_PLAN_PRICE, "$5")
                                    .apply()
                            } else {
                                appPrefs.edit()
                                    .putLong(SettingsStore.KEY_PLAN_EXPIRY_MS, activatedAt + durMs)
                                    .putString(SettingsStore.KEY_ACTIVE_PLAN_DURATION, planDurationLabel(githubPlan))
                                    .putString(SettingsStore.KEY_ACTIVE_PLAN_PRICE, planPrice(githubPlan))
                                    .apply()
                            }
                        }
                    } else {
                        edit.remove(SettingsStore.KEY_GITHUB_APPROVED_PLAN)
                        val appPrefs = SettingsStore.prefs(ctx)
                        appPrefs.edit()
                            .remove(SettingsStore.KEY_ACTIVE_PLAN_NAME)
                            .remove(SettingsStore.KEY_PLAN_STARTED_MS)
                            .remove(SettingsStore.KEY_PLAN_EXPIRY_MS)
                            .remove(SettingsStore.KEY_ACTIVE_PLAN_DURATION)
                            .remove(SettingsStore.KEY_ACTIVE_PLAN_PRICE)
                            .apply()
                    }
                    edit.apply()
                    return@withContext state
                }
            } catch (e: Exception) {
                return@withContext fallback(p, now, lastStateName, e.javaClass.simpleName)
            }
        }

    /** Returns a Triple of (State, planName, userName) from the GitHub JSON entry.
     *  planName and userName are empty strings when not specified or not applicable. */
    private fun decide(json: String, deviceId: String): Triple<State, String, String> {
        return try {
            val root = JSONObject(json)

            // Per-user kill-switch list — wins over approved.
            val blocked = root.optJSONArray("blocked") ?: JSONArray()
            for (i in 0 until blocked.length()) {
                val v = blocked.opt(i)
                val id = when (v) {
                    is JSONObject -> v.optString("android_id", "")
                    is String -> v
                    else -> ""
                }
                if (id.equals(deviceId, ignoreCase = true)) return Triple(State.Blocked, "", "")
            }

            val approved = root.optJSONArray("approved") ?: JSONArray()
            for (i in 0 until approved.length()) {
                val v = approved.opt(i)
                val id = when (v) {
                    is JSONObject -> v.optString("android_id", "")
                    is String -> v
                    else -> ""
                }
                if (id.equals(deviceId, ignoreCase = true)) {
                    // Extract CHAND-assigned plan and name from the JSON object
                    val plan = if (v is JSONObject) v.optString("plan", "").trim() else ""
                    val userName = if (v is JSONObject) v.optString("name", "").trim() else ""
                    return Triple(State.Approved, plan, userName)
                }
            }
            Triple(State.NotApproved, "", "")
        } catch (_: Throwable) {
            Triple(State.NotApproved, "", "")
        }
    }

    /**
     * STRICT MODE fallback — called whenever the network fetch fails for
     * any reason (no internet, 404, timeout, server removed, etc.).
     *
     * Rule: if the server is unreachable → Block immediately.
     * No offline grace period. No cached-Approved bypass.
     * The only exception is GloballyDisabled (already the harshest state).
     */
    private fun fallback(p: SharedPreferences, now: Long, lastStateName: String?, why: String): State {
        if (lastStateName == "GloballyDisabled") return State.GloballyDisabled
        // All other failures → Blocked. No exceptions, no grace period.
        // If Pastebin is removed or unreachable, the app stops working.
        return State.Blocked
    }

    /** Map GitHub plan name → duration in ms (-1 = Lifetime). */
    private fun planDurationMs(plan: String): Long = when {
        plan.equals("Trial",     ignoreCase = true) -> 7L  * 24 * 3600 * 1000
        plan.equals("Monthly",   ignoreCase = true) -> 30L * 24 * 3600 * 1000
        plan.equals("Quarterly", ignoreCase = true) -> 90L * 24 * 3600 * 1000
        plan.equals("Half Year", ignoreCase = true) -> 180L * 24 * 3600 * 1000
        plan.equals("Lifetime",  ignoreCase = true) -> -1L
        else                                         -> 30L * 24 * 3600 * 1000
    }

    private fun planDurationLabel(plan: String): String = when {
        plan.equals("Trial",     ignoreCase = true) -> "7 Days"
        plan.equals("Monthly",   ignoreCase = true) -> "1 Month"
        plan.equals("Quarterly", ignoreCase = true) -> "3 Months"
        plan.equals("Half Year", ignoreCase = true) -> "6 Months"
        plan.equals("Lifetime",  ignoreCase = true) -> "Forever"
        else                                         -> "30 Days"
    }

    private fun planPrice(plan: String): String = when {
        plan.equals("Trial",     ignoreCase = true) -> "FREE"
        plan.equals("Monthly",   ignoreCase = true) -> "$0.50"
        plan.equals("Quarterly", ignoreCase = true) -> "$1"
        plan.equals("Half Year", ignoreCase = true) -> "$1.90"
        plan.equals("Lifetime",  ignoreCase = true) -> "$5"
        else                                         -> "$0.50"
    }

    private fun name(s: State): String = when (s) {
        State.Approved         -> "Approved"
        State.Blocked          -> "Blocked"
        State.NotApproved      -> "NotApproved"
        State.GloballyDisabled -> "GloballyDisabled"
        is State.OfflineUnknown -> "OfflineUnknown"
    }
}
