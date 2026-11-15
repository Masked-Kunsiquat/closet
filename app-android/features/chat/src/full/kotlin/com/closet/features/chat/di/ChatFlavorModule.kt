package com.closet.features.chat.di

import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.LanguageDetector
import com.closet.features.chat.MlKitChatDateParser
import com.closet.features.chat.MlKitLanguageDetector
import com.closet.features.chat.PatternChatRouter
import com.closet.features.chat.ai.NanoChatProvider
import com.closet.features.chat.ai.OnDeviceChatProvider
import org.koin.dsl.module

/** full flavor: ML Kit / Gemini Nano implementations of the chat capability interfaces. */
val chatFlavorModule = module {
    single<OnDeviceChatProvider> { NanoChatProvider(get()) }
    single<ChatDateParser> { MlKitChatDateParser() }
    single<LanguageDetector> { MlKitLanguageDetector() }
    single<ChatRouter> { PatternChatRouter(get(), get(), get(), get()) }
}
