package com.closet.features.chat.di

import org.koin.dsl.module

/** foss flavor: no-op capability bindings only. */
val chatFlavorModule = module {
    includes(chatNoOpModule)
}
