package com.closet.tools.demobackup

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvRowTest {

    private fun records(text: String) = Csv.parse(StringReader(text))

    @Test
    fun quotedNameWithCommasStaysOneField() {
        val rows = records("1,Men,Apparel,Topwear,Shirts,Blue,Fall,2011,Casual,\"Check, Slim Fit, Shirt\"\n")
        assertEquals(1, rows.size)
        assertEquals(10, rows[0].size)
        assertEquals("Check, Slim Fit, Shirt", StyleRow.fromFields(rows[0])?.name)
    }

    @Test
    fun escapedQuotesAndEmbeddedNewlinesParse() {
        val rows = records("a,\"say \"\"hi\"\"\",\"two\nlines\"\r\nb,c,d\r\n")
        assertEquals(listOf(listOf("a", "say \"hi\"", "two\nlines"), listOf("b", "c", "d")), rows)
    }

    @Test
    fun unquotedExtraCommasAreJoinedBackIntoTheName() {
        val rows = records("2,Women,Apparel,Topwear,Tops,Red,Summer,2012,Casual,Lace, Floral, Top\n")
        val row = StyleRow.fromFields(rows[0])!!
        assertEquals("Lace, Floral, Top", row.name)
        assertEquals("Tops", row.articleType)
        assertEquals("Red", row.baseColour)
        assertEquals("Casual", row.usage)
    }

    @Test
    fun byteOrderMarkIsIgnored() {
        val rows = records("﻿id,gender\n1,Men\n")
        assertEquals("id", rows[0][0])
    }

    @Test
    fun shortOrNamelessRowsAreRejected() {
        assertNull(StyleRow.fromFields(listOf("1", "Men", "Apparel")))
        assertNull(StyleRow.fromFields(listOf("1", "Men", "Apparel", "Topwear", "Shirts", "Blue", "Fall", "2011", "Casual", "  ")))
        assertNull(StyleRow.fromFields(listOf("x", "Men", "Apparel", "Topwear", "Shirts", "Blue", "Fall", "2011", "Casual", "Name")))
    }
}
