package com.closet.features.recommendations.scoreboard

import java.time.DayOfWeek

/** One simulated wardrobe item. Names must match the seeded lookup tables. */
data class FixtureItem(
    val key: String,
    val description: String,
    val category: String,
    val subcategory: String?,
    /** Hex `#RRGGBB` to share (0..1). */
    val colors: List<Pair<String, Double>>,
    val materials: Set<String>,
    val seasons: Set<String>,
    val occasions: Set<String>,
    /** Scoreboard-only (0..3). */
    val warmth: Int,
    /** Scoreboard-only. */
    val rainReady: Boolean,
    /** Scoreboard-only (1..5). */
    val formality: Int,
)

/** A ground-truth outfit the persona likes; [saved] ones are also saved in the app. */
data class FixtureOutfit(
    val key: String,
    val itemKeys: List<String>,
    val occasions: Set<String>,
    val minTempC: Double,
    val maxTempC: Double,
    val rainOk: Boolean,
    /** Must be greater than zero. */
    val favouriteWeight: Double,
    val saved: Boolean,
)

enum class DayKind { OFFICE, CAMPUS, ERRANDS, WEEKEND, HOME, GYM, HIKE, EVENING }

/** Twelve entries per list, January first. */
data class Climate(
    val monthlyHighC: List<Double>,
    val monthlyLowC: List<Double>,
    val monthlyRainChance: List<Double>,
    val windyChance: Double,
)

data class Persona(
    val name: String,
    val seed: Long,
    val items: List<FixtureItem>,
    val outfits: List<FixtureOutfit>,
    /** Seven entries, Monday first. */
    val weekSchedule: List<DayKind>,
    val climate: Climate,
    val wearsBeforeWash: Int,
    val laundryDay: DayOfWeek,
    val swapChance: Double,
    val homeNoShoesChance: Double,
)
