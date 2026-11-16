package com.closet.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatTest {
    @Test fun `bytes below one KiB print as whole bytes`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("1023 B", formatBytes(1023L))
    }

    @Test fun `KiB rounds to one decimal`() {
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 KB", formatBytes(1536L))
        assertEquals("1.1 KB", formatBytes(1126L))
    }

    @Test fun `just under one MiB stays in KB like String format did`() {
        assertEquals("1024.0 KB", formatBytes(1_048_575L))
    }

    @Test fun `MB and GB`() {
        assertEquals("1.5 MB", formatBytes(1_572_864L))
        assertEquals("1.0 GB", formatBytes(1_073_741_824L))
    }
}
