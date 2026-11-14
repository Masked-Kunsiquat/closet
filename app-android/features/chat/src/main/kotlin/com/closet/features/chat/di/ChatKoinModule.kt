package com.closet.features.chat.di

import com.closet.core.data.di.AiHttpClientQualifier
import com.closet.features.chat.ChatRepository
import com.closet.features.chat.ChatViewModel
import com.closet.features.chat.ai.AnthropicChatProvider
import com.closet.features.chat.ai.ChatAiProviderSelector
import com.closet.features.chat.ai.GeminiChatProvider
import com.closet.features.chat.ai.OpenAiChatProvider
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the chat feature. The flavor-specific NanoChatProvider, ChatDateParser
 * and ChatRouter live in `chatFlavorModule` (src/full and src/foss).
 */
val chatModule = module {
    // Explicit constructors: these take the AI-qualified HttpClient (the long-timeout one).
    single { AnthropicChatProvider(get(AiHttpClientQualifier), get(), get()) }
    single { GeminiChatProvider(get(AiHttpClientQualifier), get(), get()) }
    single { OpenAiChatProvider(get(AiHttpClientQualifier), get(), get()) }
    singleOf(::ChatAiProviderSelector)
    singleOf(::ChatRepository)
    viewModelOf(::ChatViewModel)
}
