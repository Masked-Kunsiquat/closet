package com.closet.core.data.repository

/** Resolves relative image paths against the images directory inside the app's documents directory. */
class IosImagePathResolver : ImagePathResolver {
    override fun absolutePath(relativePath: String): String =
        documentDirectory() + "/$IMAGES_DIR_NAME/$relativePath"
}
