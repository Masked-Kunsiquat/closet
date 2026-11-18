package com.closet.features.recommendations

import com.closet.core.data.repository.ImagePathResolver
import com.closet.core.data.time.FixedAppClock
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelImageTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val viewModel by lazy {
        RecommendationViewModel(
            recommendationRepository = mockk(relaxed = true),
            engine = mockk(relaxed = true),
            scorer = mockk(relaxed = true),
            weatherRepository = mockk(relaxed = true),
            outfitRepository = mockk(relaxed = true),
            lookupRepository = mockk(relaxed = true),
            imagePathResolver = ImagePathResolver { "/root/$it" },
            aiPrefsRepo = mockk(relaxed = true),
            clock = FixedAppClock(Instant.parse("2031-07-10T12:00:00Z")),
        )
    }

    @Test fun resolvesRelativePathThroughResolver() {
        assertEquals("/root/a.jpg", viewModel.resolveImage("a.jpg"))
    }

    @Test fun nullPathStaysNull() {
        assertNull(viewModel.resolveImage(null))
    }
}
