package com.closet.features.chat

import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ColorEntity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextBlockTest {

    private val repository = ChatRepository(mockk(), mockk(), mockk(), mockk(), mockk())

    private fun detail(id: Long, name: String, color: String) = ClothingItemDetail(
        item = ClothingItemEntity(id = id, name = name),
        wearCount = 0,
        category = null,
        subcategory = null,
        sizeValue = null,
        colors = listOf(ColorEntity(name = color)),
        materials = emptyList(),
        seasons = emptyList(),
        occasions = emptyList(),
        patterns = emptyList(),
        brand = null,
    )

    @Test
    fun `item lines carry only the ID, with no positional number`() {
        // Similarity rank puts item 2 first: a "1." prefix would compete with [ID:2].
        val block = repository.buildContextBlock(
            listOf(detail(2, "Sneakers", "Yellow"), detail(1, "Overcoat", "Grey")),
        )
        val itemLines = block.lines().drop(1)
        assertEquals(
            listOf("- [ID:2] Sneakers, Colors: Yellow", "- [ID:1] Overcoat, Colors: Grey"),
            itemLines,
        )
        itemLines.forEach { line ->
            assertTrue(line, line.startsWith("- [ID:"))
            assertFalse(line, Regex("""^\d+\. """).containsMatchIn(line))
        }
    }

    @Test
    fun `empty item list keeps the no-items message`() {
        assertEquals("No wardrobe items available.", repository.buildContextBlock(emptyList()))
    }
}
