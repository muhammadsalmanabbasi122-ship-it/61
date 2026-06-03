package com.ghosttype.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest

/**
 * Runtime decryption for the obfuscated constants generated at build
 * time by the Gradle task `generateObfConstants` (see app/build.gradle.kts).
 *
 * The encryption key is derived from `packageName + SHA-256(signing
 * cert)`, so a thief who decompiles the APK and re-signs with their
 * own keystore produces a DIFFERENT key — every Obf.decode(...) on the
 * repackaged APK then returns garbage. Things break in interesting
 * ways (the GitHub URL becomes nonsense → fetch fails → lock screen
 * forever) but no clean text leaks.
 *
 * For debug / unsigned builds where the build-time SHA can't be known,
 * the Gradle task stores PLAIN strings and sets [ObfConstants.IS_OBFUSCATED]
 * to false; in that case we just return the input unchanged.
 */
internal object Obf {

    private val keyCache = mutableMapOf<String, ByteArray>()

    /** Decrypts an obfuscated constant. Returns "" on any failure so
     *  callers can defensively check `isBlank()` and fall back to the
     *  lock screen instead of crashing. */
    fun decode(ctx: Context, encrypted: String): String {
        if (!ObfConstants.IS_OBFUSCATED) return encrypted
        return try {
            val key = derivedKey(ctx)
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val out = ByteArray(bytes.size)
            for (i in bytes.indices) {
                out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(out, Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun derivedKey(ctx: Context): ByteArray {
        val pkg = ctx.packageName
        val sha = currentSigningSha(ctx).lowercase().replace(":", "")
        val cacheK = "$pkg::$sha"
        keyCache[cacheK]?.let { return it }
        val seed = "ghosttype_obf_v1::$pkg::$sha".toByteArray(Charsets.UTF_8)
        val k = MessageDigest.getInstance("SHA-256").digest(seed)
        keyCache[cacheK] = k
        return k
    }

    /** SHA-256 of the APK's first signing cert, lowercase hex, no
     *  separators. Empty string on any platform error so callers can
     *  detect it via .isEmpty(). */
    fun currentSigningSha(ctx: Context): String {
        return try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val pi = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
            }
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                pi.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION") pi.signatures
            }
            if (sigs.isNullOrEmpty()) return ""
            MessageDigest.getInstance("SHA-256")
                .digest(sigs[0].toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } catch (_: Throwable) {
            ""
        }
    }
}
