package com.closet.features.chat.di

import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.RegexChatDateParser
import com.closet.features.chat.UnroutedChatRouter
import com.closet.features.chat.ai.OnDeviceChatProvider
import com.closet.features.chat.ai.UnavailableOnDeviceChatProvider
import org.koin.dsl.module

/** No-op capability bindings for builds without Play Services. */
val chatNoOpModule = module {
    single<OnDeviceChatProvider> { UnavailableOnDeviceChatProvider() }
    single<ChatDateParser> { RegexChatDateParser() }
    single<ChatRouter> { UnroutedChatRouter() }
}
