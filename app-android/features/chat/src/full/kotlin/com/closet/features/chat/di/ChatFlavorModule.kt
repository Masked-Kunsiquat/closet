package com.closet.features.chat.di

import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.ChatRouter
import com.closet.features.chat.ai.NanoChatProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** full flavor: NanoChatProvider(json), ChatDateParser(), ChatRouter(clothingDao, logDao, dateParser). */
val chatFlavorModule = module {
    singleOf(::NanoChatProvider)
    singleOf(::ChatDateParser)
    singleOf(::ChatRouter)
}
