package com.closet.features.chat

/**
 * Regex patterns and pure-logic matchers extracted from [ChatRouter] so that unit tests can
 * call the exact production matching/parsing functions without instantiating [ChatRouter]
 * (whose constructor calls `LanguageIdentification.getClient()`, a GMS call unavailable in
 * local unit tests).
 */
internal object ChatRouterPatterns {

    val ITEM_NAME_PATTERN = Regex(
        """(?:how many times (?:have i |did i |i've )?(?:worn|wear)|wear count(?:\s+for)?|worn|wear) (?:my |the )?(.+?)(?:\?|$)"""
    )

    val DAYS_PATTERN = Regex("""(\d+)\s*(days?|weeks?)\b""")

    // Anchored at ^ so only the leading "what" is checked against the 15-char gap limit.
    // Without the anchor, containsMatchIn() would find a second "what" mid-sentence
    // (e.g. "what goes with what i wore on tuesday") and incorrectly trigger routing.
    val WORE_ON_INTERROGATIVE_PATTERN = Regex("""^\bwhat\b.{0,15}\bi\b\s*\bwore on\b""")

    // Matches explicit never-worn history phrasings. The trailing alternative handles
    // "never been worn" which doesn't fit the .{0,20} gap structure of the first branch.
    private val NEVER_WORN_PATTERN = Regex(
        """\bnever\b.{0,20}\b(?:worn|wore|tried on)\b|\bnever been worn\b"""
    )

    /**
     * Extracts an item name from a wear-count query.
     * Returns null if no name can be confidently extracted.
     */
    fun matchItemName(lower: String): String? {
        val match = ITEM_NAME_PATTERN.find(lower) ?: return null
        return match.groupValues[1].trim().removeSuffix("?").trim().takeIf { it.isNotBlank() }
    }

    /**
     * Extracts a day count from a not-worn-since query.
     * Converts "2 weeks" → 14. Returns null for "lately"/"recently".
     */
    fun parseDays(lower: String): Int? {
        val match = DAYS_PATTERN.find(lower) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        return if ("week" in unit) count * 7 else count
    }

    fun matchesWoreOnInterrogative(lower: String): Boolean =
        WORE_ON_INTERROGATIVE_PATTERN.containsMatchIn(lower)

    fun matchesNeverWorn(lower: String): Boolean =
        NEVER_WORN_PATTERN.containsMatchIn(lower)

    fun matchesNotWornSince(lower: String): Boolean =
        ("haven't worn" in lower || "havent worn" in lower ||
         "not worn" in lower || "unworn" in lower) &&
        (DAYS_PATTERN.containsMatchIn(lower) || "lately" in lower || "recently" in lower)
}
