package com.ghosttype.security

import java.security.MessageDigest

internal object PastebinSecrets {
    private const val KEY = "GHOSTPRO"

    private val XOR_APPROVAL = hexToBytes("2f3c3b23276a7d6037293c2731323b21692b203e7b22333868300d000068061a20")
    private val XOR_CRASH    = hexToBytes("2f3c3b23276a7d6037293c2731323b21692b203e7b22333868021a1e0108360e25")
    private val XOR_UPDATE   = hexToBytes("2f3c3b23276a7d6037293c2731323b21692b203e7b223338680b7b3404620a0902")

    private val APPROVAL_HASH = "6843ca631417207a122c9377aa27d44bd45e8f4d842a7f46f167d671fa934174"
    private val CRASH_HASH    = "8ffdfe1756d97d2dfc4ed15bba6d6b98aa0d4905e6d845374238410c568f8a69"
    private val UPDATE_HASH   = "51416e2072ad68003795a9c80d4034fd2753aa302a5df3a1afabea2adf9af34e"

    val APPROVAL_URL: String by lazy { decode(XOR_APPROVAL) }
    val CRASH_URL: String    by lazy { decode(XOR_CRASH) }
    val UPDATE_URL: String   by lazy { decode(XOR_UPDATE) }

    private fun decode(data: ByteArray): String {
        return data.mapIndexed { i, b ->
            (b.toInt() xor KEY[i % KEY.length].code).toChar()
        }.joinToString("")
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun validateIntegrity() {
        val md = MessageDigest.getInstance("SHA-256")
        val a = md.digest(APPROVAL_URL.toByteArray()).joinToString("") { "%02x".format(it) }
        val c = md.digest(CRASH_URL.toByteArray()).joinToString("") { "%02x".format(it) }
        val u = md.digest(UPDATE_URL.toByteArray()).joinToString("") { "%02x".format(it) }
        if (a != APPROVAL_HASH || c != CRASH_HASH || u != UPDATE_HASH) {
            throw SecurityException("Pastebin integrity check failed")
        }
    }
}
