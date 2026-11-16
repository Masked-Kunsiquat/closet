package com.closet.core.data.repository

/** Name of the app-owned directory that holds all stored images. */
const val IMAGES_DIR_NAME = "closet_images"

/** Turns a relative image path, as stored in the database, into an absolute path for image loaders. */
fun interface ImagePathResolver {
    fun absolutePath(relativePath: String): String
}
