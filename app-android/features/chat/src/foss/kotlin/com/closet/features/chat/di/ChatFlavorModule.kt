package com.closet.features.chat.di

import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.ai.NanoChatProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** foss flavor stubs: NanoChatProvider(json), ChatDateParser(), ChatRouter() (no-arg). */
val chatFlavorModule = module {
    singleOf(::NanoChatProvider)
    singleOf(::ChatDateParser)
    singleOf(::ChatRouter)
}
