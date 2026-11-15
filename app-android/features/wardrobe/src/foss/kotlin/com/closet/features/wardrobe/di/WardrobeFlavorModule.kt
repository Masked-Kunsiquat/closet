package com.closet.features.wardrobe.di

import org.koin.dsl.module

/** foss flavor: no-op stubs. */
val wardrobeFlavorModule = module {
    includes(wardrobeNoOpModule)
}
