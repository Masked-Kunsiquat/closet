package com.closet.tools.demobackup

import com.closet.core.data.getDatabaseBuilder
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.BrandRepository
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.util.DataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupOptions(
    val count: Int = 300,
    val seed: Long = 42,
    val history: Boolean = true,
    /** The newest day wear logs can land on; the default is the day the tool runs. */
    val today: LocalDate = LocalDate.now(),
    val outfitCount: Int = 25,
    val logCount: Int = 120,
    val historyDays: Int = 180,
    /** Share of items (0.0..1.0) that get price, brand, materials, patterns and status details. */
    val enrichShare: Double = 0.65,
)

class BuildSummary(
    val stats: LoadStats,
    /** Mapped items per category before sampling. */
    val pool: Map<String, Int>,
    /** Items per category in the backup. */
    val perCategory: Map<String, Int>,
    val outfits: Int,
    val logs: Int,
    /** Items given price/brand/material/pattern/status details. */
    val enriched: Int,
    val out: File,
) {
    val sampled: Int get() = perCategory.values.sum()

    fun render(): String = buildString {
        appendLine("Rows read:        ${stats.rowsRead}")
        appendLine("Mapped:           ${stats.mapped}")
        appendLine("Skipped (gender): ${stats.skippedGender}")
        appendLine("Skipped (articleType): ${stats.skippedArticleType}")
        appendLine("Skipped (missing image): ${stats.skippedMissingImage}")
        if (stats.skippedInvalid > 0) appendLine("Skipped (unreadable row): ${stats.skippedInvalid}")
        appendLine("Items in backup:  $sampled")
        appendLine("Per category (in backup / mapped):")
        for (category in Mapping.categoryOrder) {
            val available = pool[category] ?: continue
            appendLine("  ${category.padEnd(22)} ${(perCategory[category] ?: 0).toString().padStart(4)} / $available")
        }
        appendLine("With details:     $enriched")
        appendLine("Outfits:          $outfits")
        append("Wear logs:        $logs")
    }
}

/** Builds the demo wardrobe and writes it as a `.hangr` archive the app's Restore accepts. */
object DemoBackup {

    private const val DB_NAME = "closet.db"
    private const val IMAGES_DIR = "images"
    private const val APP_VERSION = "demo-backup"

    /** The `manifest.json` keys; pinned to the app's `BackupManifest` by `BackupWriterTest`. */
    val MANIFEST_FIELDS = listOf("schemaVersion", "appVersion", "createdAt", "imageCount", "apiKeysExcluded")

    suspend fun build(datasetDir: File, out: File, options: BackupOptions): BuildSummary =
        withContext(Dispatchers.IO) {
            val loaded = Dataset.load(datasetDir)
            val chosen = Sampler.sample(loaded.items, options.count, options.seed)

            val work = Files.createTempDirectory("hangr-demo-backup").toFile()
            try {
                val imagesDir = File(work, IMAGES_DIR).also { it.mkdirs() }
                val dbFile = File(work, DB_NAME)
                val schemaVersion: Int
                val outfits: Int
                val logs: Int
                val enrichedCount: Int

                val db = getDatabaseBuilder(dbFile).build()
                try {
                    val lookups = Lookups.load(db)
                    val clothing = ClothingRepository(db, db.clothingDao())
                    val enricher = Enricher(options.seed, options.today, options.enrichShare, options.historyDays)
                    val brands = BrandRepository(db.brandDao())
                    val brandIds = HashMap<String, Long>()
                    var enriched = 0

                    // Insert through the repository the app uses, so junctions are delete-then-insert
                    // and semantic_description is generated.
                    val dbIds = LinkedHashMap<Long, Long>()
                    for (item in chosen) {
                        val fileName = imageName(item)
                        item.image.copyTo(File(imagesDir, fileName), overwrite = true)
                        val subcategory = lookups.subcategory(item.category, item.subcategory)
                        val details = enricher.detailsFor(item)
                        if (!details.isBare) enriched++
                        val brandId = details.brand?.let { name ->
                            brandIds.getOrPut(name) { brands.insertBrand(name).unwrap("insert brand $name") }
                        }
                        val entity = ClothingItemEntity(
                            name = item.name,
                            categoryId = subcategory.categoryId,
                            subcategoryId = subcategory.id,
                            imagePath = fileName,
                            brandId = brandId,
                            purchasePrice = details.price,
                            purchaseDate = details.purchaseDate,
                            isFavorite = if (details.favorite) 1 else 0,
                            washStatus = if (details.dirty) WashStatus.Dirty else WashStatus.Clean,
                            status = details.retired?.let { label -> ClothingStatus.entries.first { it.label == label } }
                                ?: ClothingStatus.Active,
                        )
                        val result = clothing.insertItemWithAttributes(
                            item = entity,
                            colors = listOfNotNull(item.color?.let { lookups.color(it) }),
                            seasonIds = listOfNotNull(item.season?.let { lookups.season(it) }),
                            occasionIds = listOfNotNull(item.occasion?.let { lookups.occasion(it) }),
                            materialIds = details.materials.map { lookups.material(it) },
                            patternIds = details.patterns.map { lookups.pattern(it) },
                        )
                        dbIds[item.id] = result.unwrap("insert item ${item.id}")
                    }
                    // The insert path vectorizes in the background; do it inline so every
                    // description is written before the database is closed.
                    dbIds.values.forEach { clothing.revectorizeItem(it) }

                    if (options.history) {
                        val history = History(db, options, chosen, dbIds).write()
                        outfits = history.first
                        logs = history.second
                    } else {
                        outfits = 0
                        logs = 0
                    }

                    enrichedCount = enriched
                    schemaVersion = db.databaseVersion()
                    db.checkpointWal()
                } finally {
                    db.close()
                }

                val imageCount = writeArchive(work, out, schemaVersion)
                check(imageCount == chosen.size) { "expected ${chosen.size} images, archived $imageCount" }

                BuildSummary(
                    stats = loaded.stats,
                    pool = loaded.items.groupingBy { it.category }.eachCount(),
                    perCategory = chosen.groupingBy { it.category }.eachCount(),
                    outfits = outfits,
                    logs = logs,
                    enriched = enrichedCount,
                    out = out,
                )
            } finally {
                work.deleteRecursively()
            }
        }

    /** A stable UUID-style `.jpg` name per dataset id, like the app's `<uuid>.jpg` images. */
    private fun imageName(item: DemoItem): String =
        "${UUID.nameUUIDFromBytes("hangr-demo-${item.id}".toByteArray())}.jpg"

    private fun writeArchive(work: File, out: File, schemaVersion: Int): Int {
        val images = File(work, IMAGES_DIR).listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name }
        val manifest = JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(schemaVersion),
                "appVersion" to JsonPrimitive(APP_VERSION),
                "createdAt" to JsonPrimitive(System.currentTimeMillis()),
                "imageCount" to JsonPrimitive(images.size),
                "apiKeysExcluded" to JsonPrimitive(true),
            )
        ).toString()
        out.absoluteFile.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_NAME))
            File(work, DB_NAME).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            for (image in images) {
                zip.putNextEntry(ZipEntry("$IMAGES_DIR/${image.name}"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        return images.size
    }
}

internal fun <T> DataResult<T>.unwrap(what: String): T = when (this) {
    is DataResult.Success -> data
    is DataResult.Error -> throw IllegalStateException("Failed to $what", throwable)
    DataResult.Loading -> error("Unexpected Loading result for $what")
}
