package com.closet.features.wardrobe

import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.core.data.repository.ClothingRepository
import com.closet.core.data.repository.LookupRepository
import com.closet.core.data.util.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(ColorRedetector.Proposal.Empty, redetector().propose(null))
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `an image is measured against the reference shades`() = runTest {
        photoProcessor.colorsByPath["item.png"] = extracted

        assertEquals(ColorRedetector.Proposal.Found(extracted), redetector().propose("item.png"))
        assertEquals(listOf("item.png"), photoProcessor.extractCalls)
        assertEquals(listOf(shades), photoProcessor.extractShades)
    }

    @Test
    fun `a shades error fails with its cause and does not measure`() = runTest {
        val cause = RuntimeException("db")
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Error(cause)
        photoProcessor.colorsByPath["item.png"] = extracted

        assertEquals(ColorRedetector.Proposal.Failed(cause), redetector().propose("item.png"))
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `shades not loaded fails without a cause`() = runTest {
        coEvery { lookupRepository.getReferenceShades() } returns DataResult.Loading
        photoProcessor.colorsByPath["item.png"] = extracted

        assertEquals(ColorRedetector.Proposal.Failed(null), redetector().propose("item.png"))
        assertTrue(photoProcessor.extractCalls.isEmpty())
    }

    @Test
    fun `an extraction that finds nothing is empty`() = runTest {
        // No colours registered for a.png. ItemPhotoProcessor also reports an undecodable photo
        // this way, so that stays Empty too.
        assertEquals(ColorRedetector.Proposal.Empty, redetector().propose("a.png"))
        assertEquals(listOf("a.png"), photoProcessor.extractCalls)
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

        assertTrue(redetector.propose("a.png") is ColorRedetector.Proposal.Failed)
        assertEquals(ColorRedetector.Proposal.Found(extracted), redetector.propose("a.png"))
    }

    @Test
    fun `an extraction that throws fails with its cause`() = runTest {
        val cause = IllegalStateException("decode")
        photoProcessor.extractGate = { throw cause }

        assertEquals(ColorRedetector.Proposal.Failed(cause), redetector().propose("a.png"))
    }

    @Test
    fun `a cancelled extraction propagates`() = runTest {
        photoProcessor.extractGate = { throw CancellationException("stop") }

        assertFailsWith<CancellationException> { redetector().propose("a.png") }
    }

    @Test
    fun `apply delegates to applyRedetectedColors with the measured photo`() = runTest {
        coEvery { clothingRepository.applyRedetectedColors(7, extracted, "item.png") } returns DataResult.Success(true)

        val result = redetector().apply(7, extracted, measuredImagePath = "item.png")

        assertEquals(DataResult.Success(true), result)
        coVerify(exactly = 1) { clothingRepository.applyRedetectedColors(7, extracted, "item.png") }
    }

    @Test
    fun `apply passes the only-if-uncurated guard through`() = runTest {
        coEvery {
            clothingRepository.applyRedetectedColors(7, extracted, "item.png", onlyIfUncurated = true)
        } returns DataResult.Success(false)

        val result = redetector().apply(7, extracted, measuredImagePath = "item.png", onlyIfUncurated = true)

        assertEquals(DataResult.Success(false), result)
        coVerify(exactly = 1) {
            clothingRepository.applyRedetectedColors(7, extracted, "item.png", onlyIfUncurated = true)
        }
    }
}
