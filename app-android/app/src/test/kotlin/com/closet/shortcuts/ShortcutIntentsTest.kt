package com.closet.shortcuts

import android.content.Intent
import com.closet.shared.AppShortcut
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutIntentsTest {

    private fun intent(action: String?, categoryId: Long? = null): Intent = mockk {
        every { this@mockk.action } returns action
        every { getLongExtra(ShortcutActions.EXTRA_CATEGORY_ID, -1L) } returns (categoryId ?: -1L)
    }

    @Test fun quickAdd() =
        assertEquals(AppShortcut.QuickAdd, intent(ShortcutActions.ACTION_QUICK_ADD).toAppShortcut())

    @Test fun logFit() =
        assertEquals(AppShortcut.LogFit, intent(ShortcutActions.ACTION_LOG_FIT).toAppShortcut())

    @Test fun laundryDay() =
        assertEquals(AppShortcut.LaundryDay, intent(ShortcutActions.ACTION_LAUNDRY_DAY).toAppShortcut())

    @Test fun categoryWithId() =
        assertEquals(AppShortcut.Category(7L), intent(ShortcutActions.ACTION_CATEGORY, 7L).toAppShortcut())

    @Test fun categoryWithoutExtraIsNull() =
        assertEquals(AppShortcut.Category(null), intent(ShortcutActions.ACTION_CATEGORY).toAppShortcut())

    @Test fun categoryWithMinusOneIsNull() =
        assertEquals(AppShortcut.Category(null), intent(ShortcutActions.ACTION_CATEGORY, -1L).toAppShortcut())

    @Test fun unknownActionIsNull() = assertNull(intent("android.intent.action.MAIN").toAppShortcut())

    @Test fun nullActionIsNull() = assertNull(intent(null).toAppShortcut())
}
