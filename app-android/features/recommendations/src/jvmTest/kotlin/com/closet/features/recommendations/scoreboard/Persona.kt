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
) {
    /**
     * Fails with [IllegalArgumentException], naming the bad field, unless the persona has the
     * expected shape. [Simulator] and [FixtureWriter] call it before they use a persona.
     */
    fun validate() {
        fun months(name: String, list: List<Double>) =
            require(list.size == 12) { "Persona ${this.name}: climate.$name needs 12 entries, has ${list.size}" }
        fun chance(name: String, value: Double) =
            require(value in 0.0..1.0) { "Persona ${this.name}: $name must be in 0..1, is $value" }

        months("monthlyHighC", climate.monthlyHighC)
        months("monthlyLowC", climate.monthlyLowC)
        months("monthlyRainChance", climate.monthlyRainChance)
        climate.monthlyRainChance.forEach { chance("climate.monthlyRainChance", it) }
        chance("climate.windyChance", climate.windyChance)
        chance("swapChance", swapChance)
        chance("homeNoShoesChance", homeNoShoesChance)
        require(weekSchedule.size == 7) { "Persona $name: weekSchedule needs 7 entries, has ${weekSchedule.size}" }
        require(wearsBeforeWash >= 1) { "Persona $name: wearsBeforeWash must be at least 1, is $wearsBeforeWash" }
        fun requireUnique(kind: String, keys: List<String>) {
            val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) { "Persona $name: duplicate $kind key(s) $duplicates" }
        }
        requireUnique("item", items.map { it.key })
        requireUnique("outfit", outfits.map { it.key })
        val keys = items.map { it.key }.toSet()
        for (outfit in outfits) {
            require(outfit.itemKeys.isNotEmpty()) { "Persona $name: outfit ${outfit.key} has no items" }
            require(outfit.favouriteWeight > 0.0) {
                "Persona $name: outfit ${outfit.key} favouriteWeight must be > 0, is ${outfit.favouriteWeight}"
            }
            outfit.itemKeys.forEach { key ->
                require(key in keys) { "Unknown item key: $key in outfit ${outfit.key}" }
            }
        }
    }
}
