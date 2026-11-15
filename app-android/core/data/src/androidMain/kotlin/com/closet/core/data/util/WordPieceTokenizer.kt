package com.closet.core.data.util

import java.io.InputStream
import java.text.Normalizer

/**
 * Output of [WordPieceTokenizer.encode]: three parallel arrays of the same length
 * ([maxLength]) ready to feed directly into the ONNX model.
 *
 * @property inputIds     token IDs — `[CLS] tokens… [SEP] [PAD]…`
 * @property attentionMask 1 for real tokens (including CLS/SEP), 0 for padding
 * @property tokenTypeIds all-zero for single-sequence inputs (segment A only)
 */
data class TokenizerOutput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
)

/**
 * Minimal BERT-style WordPiece tokenizer for `all-MiniLM-L6-v2` (uncased).
 *
 * Reads the vocabulary from a `vocab.txt` stream (one token per line; line index = token ID)
 * and produces fixed-length [TokenizerOutput] arrays suitable for the ONNX model.
 *
 * Pipeline:
 * 1. **Basic tokenization** — lowercase → unicode NFD + strip combining marks → whitespace split
 *    → punctuation split.
 * 2. **WordPiece** — greedy longest-prefix match; continuation pieces are prefixed with `##`.
 * 3. **Encoding** — prepend `[CLS]`, append `[SEP]`, truncate at `maxLength - 2` content
 *    tokens, zero-pad to `maxLength`.
 *
 * @param vocabStream a stream for `vocab.txt`; consumed and closed during construction.
 */
class WordPieceTokenizer(vocabStream: InputStream) {

    private val vocab: Map<String, Int>
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    private val unkId: Int

    companion object {
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"
        private const val CONTINUATION_PREFIX = "##"

        /** Words longer than this many characters are replaced wholesale with [UNK_TOKEN]. */
        private const val MAX_CHARS_PER_WORD = 100
    }

    init {
        vocab = HashMap<String, Int>().also { map ->
            vocabStream.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, token -> map[token.trimEnd()] = index }
            }
        }
        clsId = vocab[CLS_TOKEN] ?: 101
        sepId = vocab[SEP_TOKEN] ?: 102
        padId = vocab[PAD_TOKEN] ?: 0
        unkId = vocab[UNK_TOKEN] ?: 100
    }

    /**
     * Tokenizes [text] and encodes it as fixed-length arrays of length [maxLength].
     *
     * Content tokens are truncated to `maxLength - 2` to leave room for CLS and SEP.
     */
    fun encode(text: String, maxLength: Int = 128): TokenizerOutput {
        require(maxLength >= 2) {
            "maxLength must be at least 2 to accommodate [CLS] and [SEP] tokens, got $maxLength"
        }
        val contentTokenIds = tokenize(text).map { vocab[it] ?: unkId }

        // Truncate to leave room for CLS and SEP
        val maxContent = maxLength - 2
        val truncated = if (contentTokenIds.size > maxContent) contentTokenIds.take(maxContent)
                        else contentTokenIds

        val realLength = truncated.size + 2  // +CLS +SEP

        val inputIds     = LongArray(maxLength) { padId.toLong() }
        val attentionMask = LongArray(maxLength) { 0L }
        val tokenTypeIds = LongArray(maxLength) { 0L }

        inputIds[0] = clsId.toLong()
        attentionMask[0] = 1L

        for ((i, id) in truncated.withIndex()) {
            inputIds[i + 1] = id.toLong()
            attentionMask[i + 1] = 1L
        }

        inputIds[realLength - 1] = sepId.toLong()
        attentionMask[realLength - 1] = 1L

        return TokenizerOutput(inputIds, attentionMask, tokenTypeIds)
    }

    // ─── Internal tokenization ────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        for (word in basicTokenize(text)) {
            tokens.addAll(wordPieceTokenize(word))
        }
        return tokens
    }

    /**
     * Lowercases, strips accents (NFD + remove Mn-category code points), then splits on
     * whitespace and punctuation boundaries.
     */
    private fun basicTokenize(text: String): List<String> {
        // 1. Clean control characters and normalise whitespace
        val cleaned = buildString(text.length) {
            for (ch in text) {
                val cp = ch.code
                // Always drop the null character and Unicode replacement character.
                if (cp == 0 || cp == 0xFFFD) continue
                // Drop non-whitespace control characters (e.g. BEL, ESC) but preserve
                // whitespace controls (\n, \t, etc.) so token boundaries are maintained.
                if (ch.isISOControl() && !ch.isWhitespace()) continue
                append(if (ch.isWhitespace()) ' ' else ch)
            }
        }.trim().lowercase()

        // 2. Unicode NFD + strip combining (accent) marks
        val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .filter { it.category != CharCategory.NON_SPACING_MARK }

        // 3. Split on whitespace, then further split each piece on punctuation boundaries
        return normalized.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .flatMap { splitOnPunctuation(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Inserts split points around punctuation characters.
     * Returns a list of non-empty strings; punctuation characters become their own tokens.
     */
    private fun splitOnPunctuation(word: String): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in word) {
            if (isPunctuation(ch)) {
                if (buf.isNotEmpty()) { result.add(buf.toString()); buf.clear() }
                result.add(ch.toString())
            } else {
                buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) result.add(buf.toString())
        return result
    }

    /**
     * Returns `true` for ASCII punctuation ranges and Unicode punctuation categories.
     * Matches the original BERT tokenizer's `_is_punctuation` function.
     */
    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        // ASCII punctuation ranges
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (ch.category) {
            CharCategory.CONNECTOR_PUNCTUATION,
            CharCategory.DASH_PUNCTUATION,
            CharCategory.START_PUNCTUATION,
            CharCategory.END_PUNCTUATION,
            CharCategory.INITIAL_QUOTE_PUNCTUATION,
            CharCategory.FINAL_QUOTE_PUNCTUATION,
            CharCategory.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    /**
     * Greedy longest-prefix WordPiece algorithm.
     *
     * Returns `["[UNK]"]` if:
     * - [word] exceeds [MAX_CHARS_PER_WORD] characters, or
     * - any sub-word cannot be found in the vocabulary.
     */
    private fun wordPieceTokenize(word: String): List<String> {
        if (word.length > MAX_CHARS_PER_WORD) return listOf(UNK_TOKEN)

        val subTokens = mutableListOf<String>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var found: String? = null

            // Greedily find the longest sub-string from `start` that is in vocab
            while (start < end) {
                val substr = word.substring(start, end)
                val candidate = if (start == 0) substr else "$CONTINUATION_PREFIX$substr"
                if (vocab.containsKey(candidate)) {
                    found = candidate
                    break
                }
                end--
            }

            if (found == null) return listOf(UNK_TOKEN)
            subTokens.add(found)
            start = end
        }

        return subTokens
    }
}
