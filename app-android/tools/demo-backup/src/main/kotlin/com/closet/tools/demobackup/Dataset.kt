package com.closet.tools.demobackup

import java.io.File
import java.util.Random

/** A dataset row that mapped cleanly and has a photo; ready to become a closet item. */
data class DemoItem(
    val id: Long,
    val gender: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val color: String?,
    val season: String?,
    val occasion: String?,
    val image: File,
)

data class LoadStats(
    val rowsRead: Int,
    val mapped: Int,
    val skippedInvalid: Int,
    val skippedGender: Int,
    val skippedArticleType: Int,
    val skippedMissingImage: Int,
)

class LoadResult(val items: List<DemoItem>, val stats: LoadStats)

object Dataset {

    /** Reads `styles.csv` and checks `images/<id>.jpg` under [dir]. Nothing in [dir] is modified. */
    fun load(dir: File): LoadResult {
        val csv = File(dir, "styles.csv")
        require(csv.isFile) { "styles.csv not found in $dir" }
        val records = csv.reader(Charsets.UTF_8).use { Csv.parse(it) }

        var rowsRead = 0
        var invalid = 0
        var gender = 0
        var articleType = 0
        var missingImage = 0
        val items = ArrayList<DemoItem>()
        val seen = HashSet<Long>()

        // Skip the header row.
        for (fields in records.drop(1)) {
            if (fields.all { it.isBlank() }) continue
            rowsRead++
            val row = StyleRow.fromFields(fields)
            if (row == null || !seen.add(row.id)) {
                invalid++
                continue
            }
            if (!Mapping.genderKept(row.gender)) {
                gender++
                continue
            }
            val target = Mapping.target(row.articleType)
            if (target == null) {
                articleType++
                continue
            }
            val image = File(dir, "images/${row.id}.jpg")
            if (!image.isFile) {
                missingImage++
                continue
            }
            items += DemoItem(
                id = row.id,
                gender = row.gender,
                name = row.name,
                category = target.category,
                subcategory = target.subcategory,
                color = Mapping.color(row.baseColour),
                season = Mapping.season(row.season),
                occasion = Mapping.occasion(row.usage),
                image = image,
            )
        }
        return LoadResult(
            items,
            LoadStats(rowsRead, items.size, invalid, gender, articleType, missingImage),
        )
    }
}

/** Deterministic stratified sampling. */
object Sampler {

    private const val MIN_PER_CATEGORY = 10

    /**
     * Picks [count] items from [pool]. Every category gets at least `min(available, 10)`; the
     * rest is split across categories in proportion to what each has left (largest remainder).
     * The result depends only on [pool]'s contents, [count] and [seed], not on its order.
     */
    fun sample(pool: List<DemoItem>, count: Int, seed: Long): List<DemoItem> {
        if (count >= pool.size) return pool.sortedBy { it.id }
        val random = Random(seed)
        val byCategory = Mapping.categoryOrder.associateWith { category ->
            pool.filter { it.category == category }.sortedBy { it.id }.toMutableList()
                .also { java.util.Collections.shuffle(it, random) }
        }.filterValues { it.isNotEmpty() }

        val quota = LinkedHashMap<String, Int>()
        var base = byCategory.mapValues { minOf(it.value.size, MIN_PER_CATEGORY) }
        if (base.values.sum() > count) {
            // Fewer slots than the per-category minimums: hand them out one category at a time.
            val shrunk = byCategory.keys.associateWith { 0 }.toMutableMap()
            var left = count
            while (left > 0) {
                for (category in byCategory.keys) {
                    if (left > 0 && shrunk.getValue(category) < base.getValue(category)) {
                        shrunk[category] = shrunk.getValue(category) + 1
                        left--
                    }
                }
            }
            base = shrunk
        }
        byCategory.keys.forEach { quota[it] = base.getValue(it) }

        val need = count - quota.values.sum()
        if (need > 0) {
            val room = byCategory.mapValues { it.value.size - quota.getValue(it.key) }
            val totalRoom = room.values.sum()
            val extra = LinkedHashMap<String, Int>()
            val remainders = ArrayList<Pair<String, Long>>()
            for ((category, r) in room) {
                val scaled = need.toLong() * r
                extra[category] = (scaled / totalRoom).toInt()
                remainders += category to scaled % totalRoom
            }
            var leftover = need - extra.values.sum()
            // Largest fractional part first; ties keep category order (sortedByDescending is stable).
            for ((category, _) in remainders.sortedByDescending { it.second }) {
                if (leftover == 0) break
                if (extra.getValue(category) < room.getValue(category)) {
                    extra[category] = extra.getValue(category) + 1
                    leftover--
                }
            }
            extra.forEach { (category, n) -> quota[category] = quota.getValue(category) + n }
        }

        return byCategory.flatMap { (category, items) -> items.take(quota.getValue(category)) }.sortedBy { it.id }
    }
}
