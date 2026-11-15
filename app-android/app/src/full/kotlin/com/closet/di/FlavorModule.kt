package com.closet.di

import com.closet.platform.gms.di.gmsModule
import org.koin.dsl.module

/** full flavor: GMS / ML Kit implementations of the feature capability interfaces. */
val flavorModule = module {
    includes(gmsModule)
}
