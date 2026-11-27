package com.closet.features.recommendations.scoreboard

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.color.ColorNamer
import com.closet.core.data.color.Oklab
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorSource
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.model.OutfitItemEntity
import com.closet.core.data.model.TaggedColor
import kotlinx.coroutines.flow.first

/** Database ids of a written [Persona], keyed by fixture key. */
class WrittenFixture(val itemIds: Map<String, Long>, val savedOutfitIds: Map<String, Long>)

/**
 * Inserts a [Persona]'s wardrobe and saved outfits through the real DAOs, the way a user would
 * file them. Items go through `ClothingDao` rather than `ClothingRepository.insertItemWithAttributes`,
 * whose vectorizer side effect needs an embedding encoder.
 *
 * Category, subcategory, material, season and occasion names resolve to the seeded lookup rows;
 * an unknown name throws [IllegalArgumentException] naming it, before anything is written. Outfit
 * role and warmth layer live on the category, so they are not set per item. Each fixture hex is
 * named by the app's [ColorNamer] over the seeded `color_shades` (Oklab nearest, ties to the lower id),
 * and stored with its share and [ColorSource.AUTO].
 */
object FixtureWriter {
    private class Resolved(
        val item: FixtureItem,
        val categoryId: Long,
        val subcategoryId: Long?,
        val colors: List<TaggedColor>,
        val seasonIds: List<Long>,
        val occasionIds: List<Long>,
        val materialIds: List<Long>,
    )

    suspend fun write(db: ClothingDatabase, persona: Persona): WrittenFixture {
        val lookup = db.lookupDao()
        val categories = lookup.getCategories().first().associate { it.name to it.id }
        val seasons = lookup.getSeasons().first().associate { it.name to it.id }
        val occasions = lookup.getOccasions().first().associate { it.name to it.id }
        val materials = lookup.getMaterials().first().associate { it.name to it.id }
        val namer = ColorNamer(lookup.getAllColorShades().map { ReferenceShade(it.colorId, it.hex) })

        fun id(map: Map<String, Long>, kind: String, name: String): Long =
            map[name] ?: throw IllegalArgumentException("Unknown $kind: $name")

        val subcategoriesByCategory = HashMap<Long, Map<String, Long>>()
        suspend fun subcategoryId(categoryId: Long, name: String): Long {
            val subs = subcategoriesByCategory.getOrPut(categoryId) {
                lookup.getSubcategories(categoryId).first().associate { it.name to it.id }
            }
            return subs[name] ?: throw IllegalArgumentException("Unknown subcategory: $name")
        }

        fun colorFor(hex: String): Long =
            namer.nearest(Oklab.fromHex(hex)) ?: throw IllegalArgumentException("No seeded colour shades to name $hex")

        val resolved = persona.items.map { item ->
            val categoryId = id(categories, "category", item.category)
            Resolved(
                item = item,
                categoryId = categoryId,
                subcategoryId = item.subcategory?.let { subcategoryId(categoryId, it) },
                colors = item.colors.map { (hex, share) ->
                    TaggedColor(colorFor(hex), hex = hex, share = share, source = ColorSource.AUTO)
                },
                seasonIds = item.seasons.map { id(seasons, "season", it) },
                occasionIds = item.occasions.map { id(occasions, "occasion", it) },
                materialIds = item.materials.map { id(materials, "material", it) },
            )
        }
        val savedOutfits = persona.outfits.filter { it.saved }
        val knownKeys = persona.items.map { it.key }.toSet()
        savedOutfits.forEach { outfit ->
            outfit.itemKeys.forEach { key ->
                require(key in knownKeys) { "Unknown item key: $key in outfit ${outfit.key}" }
            }
        }

        val clothing = db.clothingDao()
        val outfitDao = db.outfitDao()
        val itemIds = LinkedHashMap<String, Long>()
        val outfitIds = LinkedHashMap<String, Long>()
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                for (r in resolved) {
                    val itemId = clothing.insertClothingItem(
                        ClothingItemEntity(
                            name = r.item.description,
                            categoryId = r.categoryId,
                            subcategoryId = r.subcategoryId,
                        )
                    )
                    clothing.updateItemColors(itemId, r.colors)
                    clothing.updateItemSeasons(itemId, r.seasonIds)
                    clothing.updateItemOccasions(itemId, r.occasionIds)
                    clothing.updateItemMaterials(itemId, r.materialIds)
                    itemIds[r.item.key] = itemId
                }
                for (outfit in savedOutfits) {
                    val outfitId = outfitDao.insertOutfit(OutfitEntity(name = outfit.key))
                    outfitDao.insertOutfitItems(
                        outfit.itemKeys.mapIndexed { index, key ->
                            OutfitItemEntity(outfitId = outfitId, clothingItemId = itemIds.getValue(key), zIndex = index)
                        }
                    )
                    outfitIds[outfit.key] = outfitId
                }
            }
        }
        return WrittenFixture(itemIds, outfitIds)
    }
}
