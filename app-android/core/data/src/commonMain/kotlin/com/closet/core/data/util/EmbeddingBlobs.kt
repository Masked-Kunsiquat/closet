package com.closet.core.data.util

/**
 * Embedding BLOB codec: float32 values, little-endian IEEE-754, 4 bytes each.
 *
 * Byte-for-byte identical to the earlier `ByteBuffer.order(LITTLE_ENDIAN).asFloatBuffer()`
 * encoding, so blobs already stored in `item_embeddings` decode unchanged.
 */
fun FloatArray.toByteArrayLE(): ByteArray {
    val out = ByteArray(size * 4)
    for (i in indices) {
        val bits = this[i].toRawBits()
        val o = i * 4
        out[o] = bits.toByte()
        out[o + 1] = (bits ushr 8).toByte()
        out[o + 2] = (bits ushr 16).toByte()
        out[o + 3] = (bits ushr 24).toByte()
    }
    return out
}

/**
 * Decodes a little-endian float32 blob. A trailing partial float (size not a multiple of 4)
 * is dropped, as `ByteBuffer.asFloatBuffer()` did.
 */
fun ByteArray.toFloatArrayLE(): FloatArray = FloatArray(size / 4) { i ->
    val o = i * 4
    val bits = (this[o].toInt() and 0xFF) or
        ((this[o + 1].toInt() and 0xFF) shl 8) or
        ((this[o + 2].toInt() and 0xFF) shl 16) or
        ((this[o + 3].toInt() and 0xFF) shl 24)
    Float.fromBits(bits)
}
