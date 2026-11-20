package com.closet.features.wardrobe

import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorRedetectorTest {

    private val shades = listOf(ReferenceShade(colorId = 1, hex = "#FF0000"), ReferenceShade(colorId = 2, hex = "#0000FF"))
    private val extracted = listOf(ExtractedColor(colorId = 1, hex = "#EE1111", share = 0.7), ExtractedColor(colorId = 2, hex = "#1111EE", share = 0.3))

    private val photoProcessor = FakeItemPhotoProcessor()
    private val lookupRepository: LookupRepository = mockk(relaxed = true) {
        coEvery { getReferenceShades() } returns DataResult.Success(shades)
    }
    private val clothingRepository: ClothingRepository = mockk(relaxed = true)

    private fun redetector() = ColorRedetector(photoProcessor, lookupRepository, clothingRepository)

    @Test
    fun `no image proposes nothing and measures nothing`() = runTest {
        assertEquals(emptyList(), redetector().propose(null))
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `an image is measured against the reference shades`() = runTest {
        photoProcessor.colorsByPath["item.png"] = extracted

        assertEquals(extracted, redetector().propose("item.png"))
        assertEquals(listOf("item.png"), photoProcessor.extractCalls)
        assertEquals(listOf(shades), photoProcessor.extractShades)
    }

    @Test
    fun `a shades error proposes nothing and does not measure`() = runTest {
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Error(RuntimeException("db"))
        photoProcessor.colorsByPath["item.png"] = extracted

        assertEquals(emptyList(), redetector().propose("item.png"))
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `the shades load once per instance`() = runTest {
        photoProcessor.colorsByPath["a.png"] = extracted
        val redetector = redetector()

        redetector.propose("a.png")
        redetector.propose("b.png")

        coVerify(exactly = 1) { lookupRepository.getReferenceShades() }
        assertEquals(listOf(shades, shades), photoProcessor.extractShades)
    }

    @Test
    fun `a failed shades load is retried on the next proposal`() = runTest {
        coEvery { lookupRepository.getReferenceShades() } returnsMany listOf(
            DataResult.Error(RuntimeException("db")),
            DataResult.Success(shades),
        )
        photoProcessor.colorsByPath["a.png"] = extracted
        val redetector = redetector()

        assertEquals(emptyList(), redetector.propose("a.png"))
        assertEquals(extracted, redetector.propose("a.png"))
    }

    @Test
    fun `an extraction that throws proposes nothing`() = runTest {
        photoProcessor.extractGate = { throw IllegalStateException("decode") }

        assertEquals(emptyList(), redetector().propose("a.png"))
    }

    @Test
    fun `apply delegates to applyRedetectedColors`() = runTest {
        coEvery { clothingRepository.applyRedetectedColors(7, extracted) } returns DataResult.Success(true)

        val result = redetector().apply(7, extracted)

        assertEquals(DataResult.Success(true), result)
        coVerify(exactly = 1) { clothingRepository.applyRedetectedColors(7, extracted) }
    }

    @Test
    fun `apply passes the only-if-uncurated guard through`() = runTest {
        coEvery {
            clothingRepository.applyRedetectedColors(7, extracted, onlyIfUncurated = true)
        } returns DataResult.Success(false)

        val result = redetector().apply(7, extracted, onlyIfUncurated = true)

        assertEquals(DataResult.Success(false), result)
        coVerify(exactly = 1) { clothingRepository.applyRedetectedColors(7, extracted, onlyIfUncurated = true) }
    }
}
