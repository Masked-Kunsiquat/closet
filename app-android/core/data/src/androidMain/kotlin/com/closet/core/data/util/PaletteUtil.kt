package com.closet.core.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility for extracting colors from images using the Palette library.
 */
object PaletteUtil {

    /**
     * Extracts a [Palette] from a given [File].
     */
    suspend fun extractPalette(file: File): Palette? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            Palette.from(bitmap).generate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts a [Palette] from a given [Bitmap].
     */
    suspend fun extractPalette(bitmap: Bitmap): Palette = withContext(Dispatchers.IO) {
        Palette.from(bitmap).generate()
    }
}
