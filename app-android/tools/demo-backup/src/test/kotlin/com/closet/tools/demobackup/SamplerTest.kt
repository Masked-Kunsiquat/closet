package com.closet.tools.demobackup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplerTest {

    private fun item(id: Long, category: String) = DemoItem(
        id = id, gender = "Men", name = "Item $id", category = category, subcategory = "x",
        color = null, season = null, occasion = null, image = File("unused.jpg"),
    )

    // 200 Tops, 100 Bottoms, 12 Footwear, 3 Swimwear.
    private val pool = (1L..200).map { item(it, "Tops") } +
        (201L..300).map { item(it, "Bottoms") } +
        (301L..312).map { item(it, "Footwear") } +
        (313L..315).map { item(it, "Swimwear") }

    @Test
    fun everyCategoryGetsAtLeastTenOrAllItsItems() {
        val counts = Sampler.sample(pool, 60, seed = 1).groupingBy { it.category }.eachCount()
        assertEquals(60, counts.values.sum())
        assertTrue(counts.getValue("Tops") >= 10)
        assertTrue(counts.getValue("Bottoms") >= 10)
        assertTrue(counts.getValue("Footwear") >= 10)
        assertEquals(3, counts.getValue("Swimwear"))
    }

    @Test
    fun remainderFollowsThePoolProportions() {
        val counts = Sampler.sample(pool, 150, seed = 1).groupingBy { it.category }.eachCount()
        assertEquals(150, counts.values.sum())
        assertTrue(counts.getValue("Tops") > counts.getValue("Bottoms"))
        assertTrue(counts.getValue("Bottoms") > counts.getValue("Footwear"))
    }

    @Test
    fun resultIsDeterministicAndIndependentOfInputOrder() {
        val a = Sampler.sample(pool, 80, seed = 5).map { it.id }
        val b = Sampler.sample(pool.shuffled(java.util.Random(9)), 80, seed = 5).map { it.id }
        assertEquals(a, b)
        assertTrue(a != Sampler.sample(pool, 80, seed = 6).map { it.id })
    }

    @Test
    fun countAbovePoolReturnsEverything() {
        assertEquals(pool.size, Sampler.sample(pool, 10_000, seed = 1).size)
    }

    @Test
    fun countBelowTheMinimumsStillReturnsExactlyCount() {
        assertEquals(7, Sampler.sample(pool, 7, seed = 1).size)
    }
}
