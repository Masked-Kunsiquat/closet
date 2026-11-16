package com.closet.core.data.ai.tools

import com.closet.core.data.TestDatabase
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.worker.EmbeddingWork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec hygiene for the real 8-tool registry (built on the JVM because the tools need DAOs). */
class ToolSpecTest {

    @Test
    fun everySpecHasSnakeCaseNameShortDescriptionAndUniqueName() {
        TestDatabase().use { t ->
            val db = t.db
            val encoder = object : EmbeddingEncoder {
                override val isAvailable = false
                override val modelVersion = EmbeddingWork.MODEL_VERSION
                override suspend fun encode(text: String) = Result.failure<FloatArray>(IllegalStateException())
            }
            val registry = ToolRegistry(
                listOf(
                    SearchItemsTool(db.clothingDao(), db.lookupDao(), EmbeddingIndex(db.embeddingDao()), encoder),
                    GetItemsTool(db.clothingDao()),
                    WearHistoryTool(db.clothingDao(), LogRepository(db.logDao())),
                    ItemsNotWornSinceTool(db.clothingDao(), db.lookupDao()),
                    MostWornTool(db.statsDao(), db.clothingDao(), db.lookupDao()),
                    OutfitsWornOnTool(db.logDao(), db.clothingDao()),
                    LaundryListTool(db.clothingDao()),
                    WardrobeSummaryTool(db.clothingDao(), db.statsDao()),
                ),
            )
            assertEquals(8, registry.specs.size)
            val snake = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
            for (spec in registry.specs) {
                assertTrue(snake.matches(spec.name), "name ${spec.name}")
                assertTrue(spec.description.length <= 180, "${spec.name} description is ${spec.description.length}")
                spec.params.forEach { assertTrue(snake.matches(it.name), "param ${it.name}") }
            }
            assertEquals(registry.specs.size, registry.specs.map { it.name }.toSet().size)
        }
    }
}
