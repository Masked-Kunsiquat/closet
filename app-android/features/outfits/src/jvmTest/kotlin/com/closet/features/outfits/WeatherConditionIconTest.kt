package com.closet.features.outfits

import com.closet.core.data.model.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

@OptIn(ExperimentalResourceApi::class)
class WeatherConditionIconTest {

    @Test
    fun everyConditionHasLoadableDrawableBytes() = runTest {
        val env = getSystemResourceEnvironment()
        for (condition in WeatherCondition.entries) {
            val bytes = getDrawableResourceBytes(env, condition.icon())
            assertTrue(bytes.isNotEmpty(), "No drawable bytes for $condition")
        }
    }
}
