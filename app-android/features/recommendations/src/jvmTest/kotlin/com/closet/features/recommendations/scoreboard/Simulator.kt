package com.closet.features.recommendations.scoreboard

import com.closet.core.data.ClothingDatabase
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.model.WashStatus
import java.time.LocalDate
import kotlin.random.Random

/**
 * One planned day.
 *
 * [dirtyAtMorning] is the laundry state before dressing, and the evaluation loop sets `wash_status`
 * from it before it evaluates the day. [dirtyAtNight] is the state at the start of the next day,
 * after this day's wear and any laundry; [Simulator.apply] writes the difference.
 */
data class SimDay(
    val date: LocalDate,
    val kind: DayKind,
    /** The seeded occasion name for [kind]. */
    val occasion: String?,
    val weather: DayWeather,
    val logged: Boolean,
    /** Empty when not logged. */
    val wornItemKeys: List<String>,
    /** The saved outfit's key when the wear is logged through the saved path, else null. */
    val viaSavedOutfit: String?,
    val dirtyAtMorning: Set<String>,
    val dirtyAtNight: Set<String>,
)

/**
 * Simulates a persona's year: what they wear, and the laundry that follows.
 *
 * [plan] is pure and fully determined by the persona (its seed); [apply] only writes. The outfit
 * choice follows the persona's ground truth (occasion, temperature band, rain, favourites), never
 * the engine's own constants.
 */
class Simulator(private val persona: Persona) {
    init {
        persona.validate()
    }

    /**
     * A day is attempted with probability [LOG_CHANCE] (real users skip days) and logged when a clean outfit exists.
     * An outfit suits the weather when the day's mean temperature, `(low + high) / 2`, lies within its band (inclusive).
     * Every random draw is made on every day, so a change in one day's branch never shifts later days.
     */
    fun plan(start: LocalDate, days: Int): List<SimDay> {
        val random = Random(persona.seed)
        val itemsByKey = persona.items.associateBy { it.key }
        val dirty = LinkedHashSet<String>()
        val wears = HashMap<String, Int>()
        val result = ArrayList<SimDay>(days)

        for (offset in 0 until days) {
            val date = start.plusDays(offset.toLong())
            val kind = persona.weekSchedule[date.dayOfWeek.ordinal]
            val occasion = OCCASIONS.getValue(kind)
            val weather = WeatherModel.day(persona.climate, date, random)
            val logRoll = random.nextDouble()
            val pickRoll = random.nextDouble()
            val swapRoll = random.nextDouble()
            val swapPick = random.nextDouble()
            val replacementPick = random.nextDouble()
            val shoesRoll = random.nextDouble()

            val morning = dirty.toSet()
            var worn: List<String> = emptyList()
            var viaSaved: String? = null

            val outfit = if (logRoll < LOG_CHANCE) chooseOutfit(occasion, weather, morning, pickRoll) else null
            if (outfit != null) {
                var keys = outfit.itemKeys
                var modified = false
                if (swapRoll < persona.swapChance) {
                    val index = (swapPick * keys.size).toInt().coerceAtMost(keys.size - 1)
                    val category = itemsByKey.getValue(keys[index]).category
                    val replacements = persona.items.filter {
                        it.category == category && it.key !in keys && it.key !in morning
                    }
                    if (replacements.isNotEmpty()) {
                        val replacement = replacements[(replacementPick * replacements.size).toInt().coerceAtMost(replacements.size - 1)]
                        keys = keys.toMutableList().also { it[index] = replacement.key }
                        modified = true
                    }
                }
                if (kind == DayKind.HOME && shoesRoll < persona.homeNoShoesChance) {
                    val shoeless = keys.filter { itemsByKey.getValue(it).category != FOOTWEAR }
                    if (shoeless.size != keys.size) {
                        keys = shoeless
                        modified = true
                    }
                }
                worn = keys
                viaSaved = if (outfit.saved && !modified) outfit.key else null
            }

            for (key in worn) {
                val count = (wears[key] ?: 0) + 1
                wears[key] = count
                if (count >= persona.wearsBeforeWash) dirty.add(key)
            }
            if (date.dayOfWeek == persona.laundryDay) {
                dirty.forEach { wears[it] = 0 }
                dirty.clear()
            }

            result += SimDay(
                date = date, kind = kind, occasion = occasion, weather = weather,
                logged = outfit != null, wornItemKeys = worn, viaSavedOutfit = viaSaved,
                dirtyAtMorning = morning, dirtyAtNight = dirty.toSet(),
            )
        }
        return result
    }

    /**
     * The persona's pick for the day, or null when nothing clean exists. Candidates are outfits with
     * every item clean; the strict set also suits the occasion, the temperature band (the day's mean
     * temperature within `minTempC..maxTempC`, inclusive) and the rain.
     * When it is empty the rules relax in order: ignore rain, then temperature, then occasion.
     */
    private fun chooseOutfit(occasion: String, weather: DayWeather, dirty: Set<String>, roll: Double): FixtureOutfit? {
        val clean = persona.outfits.filter { o -> o.itemKeys.none { it in dirty } }
        fun suitsOccasion(o: FixtureOutfit) = occasion in o.occasions
        fun suitsTemperature(o: FixtureOutfit) = (weather.lowC + weather.highC) / 2 in o.minTempC..o.maxTempC
        fun suitsRain(o: FixtureOutfit) = !weather.raining || o.rainOk

        val tiers = listOf(
            clean.filter { suitsOccasion(it) && suitsTemperature(it) && suitsRain(it) },
            clean.filter { suitsOccasion(it) && suitsTemperature(it) },
            clean.filter { suitsOccasion(it) },
            clean,
        )
        val pool = tiers.firstOrNull { it.isNotEmpty() } ?: return null
        val total = pool.sumOf { it.favouriteWeight }
        var target = roll * total
        for (o in pool) {
            target -= o.favouriteWeight
            if (target < 0.0) return o
        }
        return pool.last()
    }

    /** Writes one planned day's log (if any) and the laundry changes it causes. */
    suspend fun apply(db: ClothingDatabase, fixture: WrittenFixture, day: SimDay) {
        if (day.logged) {
            val log = OutfitLogEntity(
                outfitId = day.viaSavedOutfit?.let { fixture.savedOutfitIds.getValue(it) },
                date = day.date.toString(),
                temperatureLow = day.weather.lowC,
                temperatureHigh = day.weather.highC,
                precipitationMm = day.weather.precipitationMm,
                windSpeedKmh = day.weather.windSpeedKmh,
            )
            if (day.viaSavedOutfit != null) {
                db.logDao().insertLogAndSnapshot(log)
            } else {
                db.logDao().insertAdHocLogAndSnapshot(log, day.wornItemKeys.map { fixture.itemIds.getValue(it) })
            }
        }
        val changed = (day.dirtyAtMorning - day.dirtyAtNight) + (day.dirtyAtNight - day.dirtyAtMorning)
        val clothing = db.clothingDao()
        for (key in changed) {
            val status = if (key in day.dirtyAtNight) WashStatus.Dirty else WashStatus.Clean
            clothing.updateWashStatus(fixture.itemIds.getValue(key), status.label, UPDATED_AT)
        }
    }

    private companion object {
        const val LOG_CHANCE = 0.70
        const val FOOTWEAR = "Footwear"
        const val UPDATED_AT = "2026-01-01T00:00:00Z"

        /** Seeded occasion names, exactly as `DatabaseSeeder` spells them. */
        val OCCASIONS = mapOf(
            DayKind.OFFICE to "Work/Business",
            DayKind.CAMPUS to "Casual",
            DayKind.ERRANDS to "Casual",
            DayKind.WEEKEND to "Casual",
            DayKind.HOME to "Loungewear",
            DayKind.GYM to "Athletic",
            DayKind.HIKE to "Outdoor/Hiking",
            DayKind.EVENING to "Date Night",
        )
    }
}
