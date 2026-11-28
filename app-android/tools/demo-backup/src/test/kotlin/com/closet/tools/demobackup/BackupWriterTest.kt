package com.closet.tools.demobackup

import androidx.room.useReaderConnection
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.getDatabaseBuilder
import com.closet.core.data.model.ClothingItemDetail
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupWriterTest {

    private val work: File = Files.createTempDirectory("demo-backup-test").toFile()

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    /** A 7-row dataset: 4 rows map, 1 is Boys, 1 has an unknown articleType, 1 has no image. */
    private fun fixtureDataset(): File {
        val dir = File(work, "dataset").also { File(it, "images").mkdirs() }
        File(dir, "styles.csv").writeText(
            """
            id,gender,masterCategory,subCategory,articleType,baseColour,season,year,usage,productDisplayName
            1,Men,Apparel,Topwear,Tshirts,Navy Blue,Summer,2012,Casual,"Plain, Navy Tee"
            2,Men,Apparel,Bottomwear,Jeans,Blue,Fall,2011,Smart Casual,Slim Fit Jeans
            3,Men,Footwear,Shoes,Casual Shoes,Black,,2013,Sports,Court Sneaker
            4,Women,Apparel,Dress,Dresses,Multi,Winter,2012,,Floral Party Dress
            5,Boys,Apparel,Topwear,Tshirts,Red,Summer,2012,Casual,Kids Tee
            6,Men,Personal Care,Fragrance,Perfume and Body Mist,NA,Fall,2012,,Cologne
            7,Men,Apparel,Topwear,Shirts,White,Summer,2012,Formal,Missing Photo Shirt
            """.trimIndent() + "\n"
        )
        for (id in 1..6) writeJpeg(File(dir, "images/$id.jpg"))
        return dir
    }

    private fun writeJpeg(file: File) {
        val image = BufferedImage(8, 10, BufferedImage.TYPE_INT_RGB)
        assertTrue(ImageIO.write(image, "jpg", file))
    }

    /** [enrichShare] defaults to 0 so the mapping assertions see bare items. */
    private fun build(history: Boolean, enrichShare: Double = 0.0): Pair<File, BuildSummary> {
        val out = File(work, "demo.hangr")
        val summary = runBlocking {
            DemoBackup.build(
                fixtureDataset(), out,
                BackupOptions(count = 300, seed = 7, history = history, enrichShare = enrichShare),
            )
        }
        return out to summary
    }

    private fun unzip(zip: File): File {
        val dest = File(work, "unzipped").also { it.mkdirs() }
        ZipFile(zip).use { z ->
            for (entry in z.entries()) {
                val target = File(dest, entry.name)
                if (entry.isDirectory) continue
                target.parentFile.mkdirs()
                z.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        return dest
    }

    private fun <T> withDb(dbFile: File, block: suspend (ClothingDatabase) -> T): T {
        val db = getDatabaseBuilder(dbFile).build()
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    @Test
    fun summaryCountsEverySkipReason() {
        val (_, summary) = build(history = false)
        assertEquals(7, summary.stats.rowsRead)
        assertEquals(4, summary.stats.mapped)
        assertEquals(1, summary.stats.skippedGender)
        assertEquals(1, summary.stats.skippedArticleType)
        assertEquals(1, summary.stats.skippedMissingImage)
        assertEquals(4, summary.sampled)
        assertTrue(summary.render().contains("Footwear"))
    }

    @Test
    fun zipHasDatabaseManifestAndOnlyTheExpectedEntries() {
        val (out, _) = build(history = false)
        ZipFile(out).use { z ->
            val names = z.entries().asSequence().map { it.name }.toList()
            assertTrue("closet.db" in names)
            assertTrue("manifest.json" in names)
            val images = names.filter { it.startsWith("images/") }
            assertEquals(4, images.size)
            assertTrue(images.all { it.endsWith(".jpg") && it.count { c -> c == '/' } == 1 })
            assertTrue(names.none { it.startsWith("prefs/") })
            assertTrue(names.none { it.endsWith("-wal") || it.endsWith("-shm") })
        }
    }

    @Test
    fun manifestFieldsMatchWhatRestoreDecodes() {
        val (out, _) = build(history = false)
        val manifest: JsonObject = ZipFile(out).use { z ->
            Json.parseToJsonElement(z.getInputStream(z.getEntry("manifest.json")).reader().readText()).jsonObject
        }
        // Pinned to BackupManifest(schemaVersion, appVersion, createdAt, imageCount, apiKeysExcluded).
        assertEquals(setOf("schemaVersion", "appVersion", "createdAt", "imageCount", "apiKeysExcluded"), manifest.keys)
        assertEquals(ClothingDatabase.VERSION, manifest.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("demo-backup", manifest.getValue("appVersion").jsonPrimitive.content)
        assertTrue(manifest.getValue("createdAt").jsonPrimitive.long > 0)
        assertEquals(4, manifest.getValue("imageCount").jsonPrimitive.int)
        assertEquals(true, manifest.getValue("apiKeysExcluded").jsonPrimitive.boolean)
    }

    @Test
    fun manifestKeysEqualTheAppsBackupManifestProperties() {
        // The tool cannot depend on :app, so read the real class's source and compare names.
        val source = File(requireNotNull(System.getProperty("closet.backupManifestSource")))
        assertTrue(source.isFile, "BackupManifest.kt not found at $source")
        val properties = Regex("""^\s*val\s+(\w+)\s*:""", RegexOption.MULTILINE)
            .findAll(source.readText()).map { it.groupValues[1] }.toSet()
        assertEquals(properties, DemoBackup.MANIFEST_FIELDS.toSet())
    }

    @Test
    fun databaseHasTheMappedItemsAndJunctions() {
        val (out, _) = build(history = false)
        val root = unzip(out)
        val details: List<ClothingItemDetail> = withDb(File(root, "closet.db")) { db ->
            assertEquals(ClothingDatabase.VERSION, db.databaseVersion())
            db.clothingDao().getAllClothingItemDetails().first()
        }
        assertEquals(4, details.size)
        val byName = details.associateBy { it.item.name }

        val tee = assertNotNull(byName["Plain, Navy Tee"])
        assertEquals("Tops", tee.category?.name)
        assertEquals("T-Shirt", tee.subcategory?.name)
        assertEquals(listOf("Navy"), tee.colors.map { it.name })
        assertEquals(listOf("Summer"), tee.seasons.map { it.name })
        assertEquals(listOf("Casual"), tee.occasions.map { it.name })
        assertEquals(null, tee.brand)
        assertEquals(null, tee.item.purchasePrice)

        val jeans = assertNotNull(byName["Slim Fit Jeans"])
        assertEquals("Royal Blue", jeans.colors.single().name)
        assertEquals("Casual", jeans.occasions.single().name) // Smart Casual -> Casual

        val shoe = assertNotNull(byName["Court Sneaker"])
        assertEquals("Sneakers", shoe.subcategory?.name)
        assertEquals("Athletic", shoe.occasions.single().name)
        assertTrue(shoe.seasons.isEmpty()) // empty season -> none

        val dress = assertNotNull(byName["Floral Party Dress"])
        assertTrue(dress.colors.isEmpty()) // Multi -> no colour, item kept
        assertTrue(dress.occasions.isEmpty())

        for (d in details) {
            assertTrue(!d.item.semanticDescription.isNullOrBlank(), "semantic_description missing for ${d.item.name}")
            val path = assertNotNull(d.item.imagePath)
            assertTrue(!path.contains('/') && !path.contains('\\'), "image_path must be a bare relative name: $path")
            assertTrue(File(root, "images/$path").isFile, "image missing in zip: $path")
        }
    }

    @Test
    fun historyAddsOutfitsAndLogsWithAtMostOneOotdPerDay() {
        val (out, summary) = build(history = true)
        assertTrue(summary.outfits > 0)
        assertTrue(summary.logs > 0)
        val root = unzip(out)
        val (logCount, ootdPerDay, snapshotRows) = withDb(File(root, "closet.db")) { db ->
            db.useReaderConnection { c ->
                val logs = c.usePrepared("SELECT COUNT(*) FROM outfit_logs") { it.step(); it.getLong(0) }
                val maxOotd = c.usePrepared(
                    "SELECT COALESCE(MAX(n), 0) FROM (SELECT COUNT(*) AS n FROM outfit_logs WHERE is_ootd = 1 GROUP BY date)"
                ) { it.step(); it.getLong(0) }
                val snaps = c.usePrepared("SELECT COUNT(*) FROM outfit_log_items") { it.step(); it.getLong(0) }
                Triple(logs, maxOotd, snaps)
            }
        }
        assertEquals(summary.logs.toLong(), logCount)
        assertTrue(ootdPerDay <= 1, "two OOTD logs share a date")
        assertTrue(snapshotRows >= logCount * 2, "logs must snapshot their items")
    }

    @Test
    fun enrichedItemsCarryPriceBrandMaterialsPatternsAndAnOlderPurchaseDate() {
        val (out, summary) = build(history = false, enrichShare = 1.0)
        assertEquals(4, summary.enriched)
        val root = unzip(out)
        val details: List<ClothingItemDetail> = withDb(File(root, "closet.db")) { db ->
            db.clothingDao().getAllClothingItemDetails().first()
        }
        val newestPurchase = BackupOptions().let { it.today.minusDays(it.historyDays.toLong()) }
        for (d in details) {
            val price = assertNotNull(d.item.purchasePrice, "price missing for ${d.item.name}")
            assertEquals(0.99, price - price.toInt(), 1e-9)
            val date = java.time.LocalDate.parse(assertNotNull(d.item.purchaseDate))
            assertTrue(!date.isAfter(newestPurchase), "purchase ${d.item.name} on $date falls inside the wear history")
            assertTrue(d.materials.isNotEmpty(), "materials missing for ${d.item.name}")
            assertTrue(d.patterns.isNotEmpty(), "patterns missing for ${d.item.name}")
        }
        val byName = details.associateBy { it.item.name }
        assertEquals(listOf("Denim"), assertNotNull(byName["Slim Fit Jeans"]).materials.map { it.name })
        assertEquals(listOf("Floral"), assertNotNull(byName["Floral Party Dress"]).patterns.map { it.name })
    }

    @Test
    fun everyEnrichmentNameIsSeeded() {
        val lookups = withDb(File(work, "lookups.db")) { db -> Lookups.load(db) }
        assertTrue(lookups.materialNames.containsAll(Enricher.allMaterials()),
            "unseeded materials: ${Enricher.allMaterials() - lookups.materialNames}")
        assertTrue(lookups.patternNames.containsAll(Enricher.allPatterns()),
            "unseeded patterns: ${Enricher.allPatterns() - lookups.patternNames}")
    }

    @Test
    fun noHistoryLeavesNoOutfitsOrLogs() {
        val (out, summary) = build(history = false)
        assertEquals(0, summary.outfits)
        assertEquals(0, summary.logs)
        val root = unzip(out)
        val logs = withDb(File(root, "closet.db")) { db ->
            db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM outfit_logs") { it.step(); it.getLong(0) } }
        }
        assertEquals(0L, logs)
    }

    @Test
    fun sameSeedGivesTheSameSampleAndHistory() {
        val dataset = fixtureDataset()
        val a = runBlocking { DemoBackup.build(dataset, File(work, "a.hangr"), BackupOptions(seed = 3)) }
        val b = runBlocking { DemoBackup.build(dataset, File(work, "b.hangr"), BackupOptions(seed = 3)) }
        assertEquals(a.outfits, b.outfits)
        assertEquals(a.logs, b.logs)
        assertEquals(a.render(), b.render())
    }
}
