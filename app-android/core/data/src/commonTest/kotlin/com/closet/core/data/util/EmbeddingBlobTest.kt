package com.closet.core.data.util

import kotlin.test.Test
import kotlin.test.assertContentEquals

class EmbeddingBlobTest {
    @Test
    fun roundTripsAndMatchesLittleEndianLayout() {
        val input = floatArrayOf(0f, 1f, -2.5f, Float.MIN_VALUE, 3.4028235e38f)
        assertContentEquals(input, input.toByteArrayLE().toFloatArrayLE())
        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F),
            floatArrayOf(1f).toByteArrayLE(),
        )
    }

    @Test
    fun ignoresTrailingPartialFloat() {
        // ByteBuffer.asFloatBuffer() drops a trailing partial float; keep that behaviour.
        val bytes = floatArrayOf(1f).toByteArrayLE() + byteArrayOf(0x01, 0x02)
        assertContentEquals(floatArrayOf(1f), bytes.toFloatArrayLE())
    }
}
