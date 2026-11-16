package com.closet.core.ui.theme

import kotlin.test.Test
import kotlin.test.assertSame

class AccentColorSchemeTest {
    @Test fun amberDarkHighContrast() =
        assertSame(amberHighContrastDarkScheme, accentColorScheme(ClosetAccent.Amber, darkTheme = true, contrastLevel = 1f))

    @Test fun amberDarkMediumContrast() =
        assertSame(amberMediumContrastDarkScheme, accentColorScheme(ClosetAccent.Amber, true, 0.5f))

    @Test fun amberDarkStandard() =
        assertSame(amberDarkScheme, accentColorScheme(ClosetAccent.Amber, true, 0f))

    @Test fun skyLightHighContrast() =
        assertSame(skyHighContrastLightScheme, accentColorScheme(ClosetAccent.Sky, false, 1f))

    @Test fun roseLightJustBelowMediumThreshold() =
        assertSame(roseLightScheme, accentColorScheme(ClosetAccent.Rose, false, 0.49f))

    @Test fun everyAccentHasItsStandardLightScheme() {
        val expected = mapOf(
            ClosetAccent.Amber to amberLightScheme,
            ClosetAccent.Coral to coralLightScheme,
            ClosetAccent.Sage to sageLightScheme,
            ClosetAccent.Sky to skyLightScheme,
            ClosetAccent.Lavender to lavenderLightScheme,
            ClosetAccent.Rose to roseLightScheme,
        )
        for (accent in ClosetAccent.entries) {
            assertSame(expected.getValue(accent), accentColorScheme(accent, false, 0f), accent.name)
        }
    }
}
