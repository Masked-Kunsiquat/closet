package com.closet.tools.demobackup

import com.closet.core.data.ClothingDatabase
import com.closet.core.data.model.ColorEntity
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.model.SubcategoryEntity
import com.closet.core.data.repository.OutfitRepository
import kotlinx.coroutines.flow.first
import java.util.Random

/** The seeded lookup rows, read back from the freshly created database. */
internal class Lookups private constructor(
    private val subcategories: Map<Pair<String, String>, SubcategoryEntity>,
    private val colors: Map<String, ColorEntity>,
    private val seasons: Map<String, Long>,
    private val occasions: Map<String, Long>,
) {
    fun subcategory(category: String, name: String): SubcategoryEntity =
        subcategories[category to name] ?: error("No seeded subcategory '$name' in '$category'")

    fun color(name: String): ColorEntity = colors[name] ?: error("No seeded color '$name'")

    fun season(name: String): Long = seasons[name] ?: error("No seeded season '$name'")

    fun occasion(name: String): Long = occasions[name] ?: error("No seeded occasion '$name'")

    companion object {
        suspend fun load(db: ClothingDatabase): Lookups {
            val dao = db.lookupDao()
            val categories = dao.getCategories().first()
            val subs = HashMap<Pair<String, String>, SubcategoryEntity>()
            for (category in categories) {
                for (sub in dao.getSubcategories(category.id).first()) subs[category.name to sub.name] = sub
            }
            return Lookups(
                subcategories = subs,
                colors = dao.getAllColors().associateBy { it.name },
                seasons = dao.getSeasons().first().associate { it.name to it.id },
                occasions = dao.getOccasions().first().associate { it.name to it.id },
            )
        }
    }
}

/** Outfits and wear logs for the sampled items. Everything derives from [BackupOptions.seed]. */
internal class History(
    private val db: ClothingDatabase,
    private val options: BackupOptions,
    private val items: List<DemoItem>,
    /** Dataset id to closet item id. */
    private val dbIds: Map<Long, Long>,
) {
    private val random = Random(options.seed * 31 + 17)

    /** Returns (outfits created, logs created). */
    suspend fun write(): Pair<Int, Int> {
        val outfitIds = createOutfits()
        return outfitIds.size to createLogs(outfitIds)
    }

    private fun pool(gender: String, category: String, subcategory: String? = null): List<DemoItem> =
        items.filter {
            it.category == category &&
                (subcategory == null || it.subcategory == subcategory) &&
                (it.gender == gender || it.gender == "Unisex")
        }

    private fun <T> List<T>.pick(): T = this[random.nextInt(size)]

    /** One Top + one Bottom + optional Footwear/Outerwear, or a Dress + Footwear. */
    private fun composeOutfit(): List<DemoItem>? {
        val gender = if (random.nextBoolean()) "Men" else "Women"
        val dresses = pool(gender, "Dresses & Jumpsuits", "Dress")
        val footwear = pool(gender, "Footwear")
        if (dresses.isNotEmpty() && footwear.isNotEmpty() && random.nextInt(4) == 0) {
            return listOf(dresses.pick(), footwear.pick())
        }
        val tops = pool(gender, "Tops")
        val bottoms = pool(gender, "Bottoms")
        if (tops.isEmpty() || bottoms.isEmpty()) return null
        val outfit = mutableListOf(tops.pick(), bottoms.pick())
        val outerwear = pool(gender, "Outerwear")
        // Roll both dice every time so the random stream does not depend on what is available.
        val wantsOuterwear = random.nextInt(100) < 40
        val wantsFootwear = random.nextInt(100) < 70
        if (wantsOuterwear && outerwear.isNotEmpty()) outfit += outerwear.pick()
        if (wantsFootwear && footwear.isNotEmpty()) outfit += footwear.pick()
        return outfit
    }

    private suspend fun createOutfits(): List<Long> {
        val repository = OutfitRepository(db, db.outfitDao())
        val seen = HashSet<Set<Long>>()
        val ids = ArrayList<Long>()
        var attempts = 0
        while (ids.size < options.outfitCount && attempts < options.outfitCount * 40) {
            attempts++
            val outfit = composeOutfit() ?: continue
            val itemIds = outfit.map { dbIds.getValue(it.id) }
            if (!seen.add(itemIds.toSet())) continue
            ids += repository.createOutfit("Outfit ${ids.size + 1}", null, itemIds).unwrap("create outfit")
        }
        return ids
    }

    /**
     * Logs go through `LogDao.insertLogAndSnapshot`, so wear counts come from the snapshot rows.
     * Most days get one log, which may be the OOTD; a few get a second, never an OOTD, so
     * `one_ootd_per_day` always holds.
     */
    private suspend fun createLogs(outfitIds: List<Long>): Int {
        if (outfitIds.isEmpty() || options.logCount <= 0) return 0
        val dao = db.logDao()
        val canDouble = outfitIds.size >= 2
        val extraTarget = if (canDouble) options.logCount / 6 else 0
        val days = (0 until options.historyDays).toMutableList().also { java.util.Collections.shuffle(it, random) }
        val primary = minOf(options.logCount - extraTarget, days.size)

        val usedOnDay = HashMap<Int, MutableSet<Long>>()
        var created = 0
        suspend fun log(dayOffset: Int, outfitId: Long, ootd: Boolean) {
            val date = options.today.minusDays(dayOffset.toLong()).toString()
            dao.insertLogAndSnapshot(OutfitLogEntity(outfitId = outfitId, date = date, isOotd = if (ootd) 1 else 0))
            usedOnDay.getOrPut(dayOffset) { HashSet() } += outfitId
            created++
        }

        for (day in days.take(primary)) {
            log(day, outfitIds.pick(), ootd = random.nextInt(100) < 70)
        }
        if (canDouble) {
            val target = minOf(options.logCount - primary, primary)
            var extras = 0
            var attempts = 0
            while (extras < target && attempts < target * 20) {
                attempts++
                val day = days[random.nextInt(primary)]
                val outfitId = outfitIds.pick()
                if (outfitId in usedOnDay.getValue(day)) continue
                log(day, outfitId, ootd = false)
                extras++
            }
        }
        return created
    }
}
