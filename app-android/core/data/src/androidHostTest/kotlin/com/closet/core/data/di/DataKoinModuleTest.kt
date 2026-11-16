package com.closet.core.data.di

import android.content.Context
import androidx.work.WorkerParameters
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import com.closet.core.data.ai.tools.ToolRegistry
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class DataKoinModuleTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test fun dataModuleVerifies() {
        dataModule.verify(
            extraTypes = listOf(
                Context::class,
                WorkerParameters::class,
                // verify() reflects on HttpClient's own constructor, which the module builds via a DSL.
                HttpClientEngine::class,
                HttpClientConfig::class,
            ),
            // ToolRegistry's `tools: List<WardrobeTool>` is built inline from the module's DAOs.
            injections = injectedParameters(definition<ToolRegistry>(List::class)),
        )
    }
}
