package com.closet.features.recommendations.di

import org.koin.dsl.module

/** foss flavor: no-op on-device provider. */
val recommendationsFlavorModule = module {
    includes(recommendationsNoOpModule)
}
