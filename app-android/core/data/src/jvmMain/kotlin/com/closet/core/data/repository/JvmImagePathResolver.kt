package com.closet.core.data.repository

import java.io.File

/** Resolves relative image paths against [imagesDir]. */
class JvmImagePathResolver(private val imagesDir: File) : ImagePathResolver {
    override fun absolutePath(relativePath: String): String = File(imagesDir, relativePath).absolutePath
}
