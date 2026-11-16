package com.closet.tools.demobackup

import java.io.Reader

/** A minimal RFC 4180 reader: quoted fields, `""` escapes, embedded commas and newlines, CRLF. */
object Csv {

    /** Reads every record from [reader]. A leading byte-order mark is dropped. */
    fun parse(reader: Reader): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var sawAnything = false
        var first = true

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = ArrayList()
            sawAnything = false
        }

        val buffered = reader.buffered()
        var next = buffered.read()
        while (next != -1) {
            val c = next.toChar()
            next = buffered.read()
            if (first) {
                first = false
                if (c == '﻿') continue
            }
            sawAnything = true
            if (inQuotes) {
                if (c == '"') {
                    if (next == '"'.code) {
                        field.append('"')
                        next = buffered.read()
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> {
                        if (next == '\n'.code) next = buffered.read()
                        endRecord()
                    }
                    '\n' -> endRecord()
                    else -> field.append(c)
                }
            }
        }
        if (sawAnything) endRecord()
        return records
    }
}

/** One usable row of `styles.csv`; only the columns the tool maps. */
data class StyleRow(
    val id: Long,
    val gender: String,
    val articleType: String,
    val baseColour: String,
    val season: String,
    val usage: String,
    val name: String,
) {
    companion object {
        private const val FIXED_COLUMNS = 9

        /**
         * Builds a row from parsed [fields]: the first 9 columns by position, then everything
         * after them rejoined with commas as the name (some names have unquoted commas).
         * Returns null for a short row, a non-numeric id or a blank name.
         */
        fun fromFields(fields: List<String>): StyleRow? {
            if (fields.size <= FIXED_COLUMNS) return null
            val id = fields[0].trim().toLongOrNull() ?: return null
            val name = fields.drop(FIXED_COLUMNS).joinToString(",").trim()
            if (name.isEmpty()) return null
            return StyleRow(
                id = id,
                gender = fields[1].trim(),
                articleType = fields[4].trim(),
                baseColour = fields[5].trim(),
                season = fields[6].trim(),
                usage = fields[8].trim(),
                name = name,
            )
        }
    }
}
