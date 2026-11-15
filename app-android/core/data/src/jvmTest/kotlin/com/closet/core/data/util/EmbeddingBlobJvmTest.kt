package com.closet.core.data.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class EmbeddingBlobJvmTest {
    /** Pins compatibility with blobs already stored by the ByteBuffer-based encoder. */
    @Test
    fun matchesJavaByteBuffer() {
        val random = Random(384)
        val arr = FloatArray(384) { random.nextFloat() * 2f - 1f }
        val expected = ByteBuffer.allocate(384 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { asFloatBuffer().put(arr) }
            .array()
        assertContentEquals(expected, arr.toByteArrayLE())

        val decoded = ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            .let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
        assertContentEquals(decoded, expected.toFloatArrayLE())
    }
}
