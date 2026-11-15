package com.closet.features.chat

import com.closet.core.data.ai.ChatResponse
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeLanguageDetector(private val english: Boolean) : LanguageDetector {
    val seen = mutableListOf<String>()
    override suspend fun isEnglish(text: String): Boolean {
        seen += text
        return english
    }
}

class PatternChatRouterTest {
    private val clothingDao = mockk<ClothingDao>()
    private val logDao = mockk<LogDao>()

    @Test fun nonEnglishMessage_isUnrouted_andTouchesNoDao() = runTest {
        val detector = FakeLanguageDetector(english = false)
        val router = PatternChatRouter(clothingDao, logDao, RegexChatDateParser(), detector)
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route("¿Cuántas prendas tengo?"))
        assertEquals(listOf("¿Cuántas prendas tengo?"), detector.seen) // original text, not lowercased
        confirmVerified(clothingDao, logDao)
    }

    @Test fun englishItemCount_isRoutedFromDao() = runTest {
        coEvery { clothingDao.getItemCount() } returns 3
        val router = PatternChatRouter(clothingDao, logDao, RegexChatDateParser(), FakeLanguageDetector(english = true))
        val result = router.route("How many items do I own?") as ChatRouter.RouterResult.Routed
        val stat = result.response as ChatResponse.WithStat
        assertEquals("You have 3 items in your wardrobe.", stat.text)
        assertEquals("Wardrobe size", stat.label)
        assertEquals("3 items", stat.value)
    }

    @Test fun englishUnmatchedMessage_isUnrouted() = runTest {
        val router = PatternChatRouter(clothingDao, logDao, RegexChatDateParser(), FakeLanguageDetector(english = true))
        assertEquals(ChatRouter.RouterResult.Unrouted, router.route("Suggest something for a wedding"))
    }
}
