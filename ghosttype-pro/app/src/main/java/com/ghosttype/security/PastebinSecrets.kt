package com.ghosttype.security

import android.content.Context
import java.security.MessageDigest

internal object PastebinSecrets {
    // XOR-encoded fragments (key derived from signing cert at runtime)
    // The URLs are split: common prefix + unique suffix per gate
    // Prefix: "https://pastebin.com/raw/"
    // Suffixes: "xBST8TUg", "JUMUXdAb", "C4gP2XFE"
    private val XOR_PREFIX = hexToBytes("2f3c20223a79646a22263b20372b2a256b3128257b20283464")
    private val XOR_SUFFIXES = arrayOf(
        hexToBytes("3f0a070671171e22"),
        hexToBytes("0d1d190711270a27"),
        hexToBytes("047c33027b1b0d00")
    )

    // Expected SHA-256 digests as raw bytes (not strings)
    private val HASH_A = hexToBytes("6843ca631417207a122c9377aa27d44bd45e8f4d842a7f46f167d671fa934174")
    private val HASH_B = hexToBytes("8ffdfe1756d97d2dfc4ed15bba6d6b98aa0d4905e6d845374238410c568f8a69")
    private val HASH_C = hexToBytes("51416e2072ad68003795a9c80d4034fd2753aa302a5df3a1afabea2adf9af34e")
    private val HASHES = arrayOf(HASH_A, HASH_B, HASH_C)

    private val keyCache = mutableMapOf<String, ByteArray>()

    fun approvalUrl(ctx: Context): String = decode(ctx, 0)
    fun crashUrl(ctx: Context): String    = decode(ctx, 1)
    fun updateUrl(ctx: Context): String   = decode(ctx, 2)

    private fun decode(ctx: Context, index: Int): String {
        val key = deriveKey(ctx)
        val prefix = xorDecode(XOR_PREFIX, key)
        val suffix = xorDecode(XOR_SUFFIXES[index], key)
        val url = prefix + suffix

        val actual = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        if (!actual.contentEquals(HASHES[index])) {
            throw SecurityException("Pastebin integrity check failed")
        }
        return url
    }

    private fun deriveKey(ctx: Context): ByteArray {
        val sha = Obf.currentSigningSha(ctx).lowercase().replace(":", "")
        keyCache[sha]?.let { return it }
        val seed = "ghosttype_pastebin_v2::${ctx.packageName}::$sha".toByteArray(Charsets.UTF_8)
        val k = MessageDigest.getInstance("SHA-256").digest(seed)
        keyCache[sha] = k
        return k
    }

    private fun xorDecode(data: ByteArray, key: ByteArray): String {
        return data.mapIndexed { i, b ->
            (b.toInt() xor key[i % key.size].toInt()).toChar()
        }.joinToString("")
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
