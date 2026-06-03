package com.ghosttype.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.ghosttype.utils.SettingsStore
import java.security.MessageDigest

/**
 * Device fingerprint used by the approval system. Combines
 * Settings.Secure.ANDROID_ID with hardware identifiers (and an
 * optional reset token), hashes them, and returns the first 16
 * hex chars.
 *
 * Calling [reset] changes the token so the next call to [get]
 * returns a completely different ID — effectively invalidating
 * any prior GitHub approval.
 */
object DeviceId {

    @SuppressLint("HardwareIds")
    fun get(ctx: Context): String {
        val androidId = try {
            Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (_: Throwable) {
            ""
        }
        val hwSig = "${Build.MANUFACTURER}|${Build.MODEL}|${Build.DEVICE}|${Build.BRAND}"
        val resetToken = SettingsStore.getDeviceResetToken(ctx)
        val seed = "ghosttype_devid_v1::$androidId::$hwSig::$resetToken".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)
        return "CHAND-" + hash.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.substring(0, 16) + "-TRICKER"
    }

    /**
     * Regenerates the reset token so the next [get] produces a
     * different device ID. The old GitHub approval is immediately
     * invalidated.
     */
    fun reset(ctx: Context) {
        SettingsStore.resetDeviceToken(ctx)
    }
}
