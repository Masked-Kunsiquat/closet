package com.closet.features.chat.di

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
 * Koin module for the chat feature. OnDeviceChatProvider, ChatDateParser, LanguageDetector
 * and ChatRouter are bound per flavor: `chatNoOpModule` (foss) or `gmsModule` in
 * `platform/gms` (full).
 */
val chatModule = module {
    singleOf(::AnthropicChatProvider)
    singleOf(::GeminiChatProvider)
    singleOf(::OpenAiChatProvider)
    singleOf(::ChatAiProviderSelector)
    singleOf(::ChatRepository)
    viewModelOf(::ChatViewModel)
}
