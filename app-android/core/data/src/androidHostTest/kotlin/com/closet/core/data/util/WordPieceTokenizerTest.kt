package com.closet.core.data.util

import kotlin.test.Test
import kotlin.test.assertContentEquals

class WordPieceTokenizerTest {
    private val tokenizer =
        WordPieceTokenizer(listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "##s"))

    @Test
    fun encodesWithClsSepAndPadding() {
        val out = tokenizer.encode("Hello worlds", 8)
        assertContentEquals(longArrayOf(2, 4, 5, 6, 3, 0, 0, 0), out.inputIds)
        assertContentEquals(longArrayOf(1, 1, 1, 1, 1, 0, 0, 0), out.attentionMask)
    }

    @Test
    fun unknownWordMapsToUnk() {
        val out = tokenizer.encode("xyz", 4)
        assertContentEquals(longArrayOf(2, 1, 3, 0), out.inputIds)
    }
}
