package com.dronebox.app

/**
 * Request signing: canonical form, SHA-256 and HMAC-SHA256, in pure Kotlin.
 *
 * Deliberately not `expect`/`actual` over platform crypto. Keeping it in
 * commonMain means the entire signing path is unit testable on the JVM with no
 * device and no simulator -- the same reasoning that keeps the firmware's
 * `auth` component free of ESP-IDF headers. The vectors in DockCryptoTest are
 * the same ones the firmware's test_auth.c asserts.
 *
 * Mirrors firmware/lift_motor/components/auth/auth.c. If one side changes, the
 * shared vectors fail on both.
 */
object DockCrypto {

    /** Mirror of `auth_canonical()`: "4:POST8:/api/cmd3:c=e1:7". */
    fun canonical(method: String, path: String, query: String, nonce: Long): ByteArray =
        canonicalBlob(method, path, query.encodeToByteArray(), nonce)

    /**
     * Mirror of `auth_canonical_blob()`. Fields are length-prefixed so no
     * rearrangement of method, path and payload can produce the same bytes --
     * a separator-joined form would let a signature captured for one request
     * authorise a different one.
     */
    fun canonicalBlob(method: String, path: String, payload: ByteArray, nonce: Long): ByteArray {
        val out = mutableListOf<Byte>()
        fun put(bytes: ByteArray) {
            out += "${bytes.size}:".encodeToByteArray().toTypedArray()
            out += bytes.toTypedArray()
        }
        put(method.encodeToByteArray())
        put(path.encodeToByteArray())
        put(payload)
        put(nonce.toString().encodeToByteArray())
        return out.toByteArray()
    }

    /** Framing for a signed BLE write: nonce (8, little-endian) ‖ sig (32) ‖ payload. */
    fun bleFrame(key: ByteArray, path: String, payload: ByteArray, nonce: Long): ByteArray {
        val sig = hmacSha256(key, canonicalBlob("BLE", path, payload, nonce))
        val head = ByteArray(8) { i -> ((nonce ushr (8 * i)) and 0xFF).toByte() }
        return head + sig + payload
    }

    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val block = 64
        var k = if (key.size > block) sha256(key) else key
        if (k.size < block) k = k + ByteArray(block - k.size)

        val inner = ByteArray(block) { (k[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(block) { (k[it].toInt() xor 0x5c).toByte() }
        return sha256(outer + sha256(inner + message))
    }

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1,
        -0x6dc07d5c, -0x54e3a12b, -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c, -0x1b64963f, -0x1041b87a,
        0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9,
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b, -0x5d40175f, -0x57e599b5,
        -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
        0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
        -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun sha256(data: ByteArray): ByteArray {
        var h0 = 0x6a09e667; var h1 = -0x4498517b; var h2 = 0x3c6ef372; var h3 = -0x5ab00ac6
        var h4 = 0x510e527f; var h5 = -0x64fa9774; var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19

        val bitLen = data.size.toLong() * 8
        val padded = data + byteArrayOf(0x80.toByte()) +
            ByteArray(((56 - (data.size + 1) % 64) + 64) % 64) +
            ByteArray(8) { i -> ((bitLen ushr (8 * (7 - i))) and 0xFF).toByte() }

        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                w[i] = (padded[chunk + i * 4].toInt() and 0xFF shl 24) or
                       (padded[chunk + i * 4 + 1].toInt() and 0xFF shl 16) or
                       (padded[chunk + i * 4 + 2].toInt() and 0xFF shl 8) or
                       (padded[chunk + i * 4 + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
        }

        val out = ByteArray(32)
        intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { i, v ->
            out[i * 4] = (v ushr 24).toByte()
            out[i * 4 + 1] = (v ushr 16).toByte()
            out[i * 4 + 2] = (v ushr 8).toByte()
            out[i * 4 + 3] = v.toByte()
        }
        return out
    }

    private fun Int.rotateRight(bits: Int) = (this ushr bits) or (this shl (32 - bits))

    fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    fun String.hexToBytes() = ByteArray(length / 2) {
        substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
