package com.closet.features.wardrobe

import com.closet.core.data.color.ExtractedColor
import com.closet.core.data.color.ReferenceShade
import com.closet.features.wardrobe.photo.ItemPhotoProcessor

/** Hand-written [ItemPhotoProcessor] that records every call and can be told to fail. */
class FakeItemPhotoProcessor : ItemPhotoProcessor {
    override var isSegmentationSupported: Boolean = true
    override var isCaptionSupported: Boolean = true

    /** Relative paths handed out by [importPhoto], in order. */
    val importResults = ArrayDeque(listOf("picked.jpg"))
    val imported = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    var ensureModelCalls = 0
    var ensureModelFailure: Throwable? = null

    var segmentedPath = "segmented.png"
    var removeBackgroundFailure: Throwable? = null
    val removeBackgroundCalls = mutableListOf<String>()

    /** Colors returned by [extractColors] per path; unknown paths yield an empty list. */
    val colorsByPath = mutableMapOf<String, List<ExtractedColor>>()
    val extractCalls = mutableListOf<String>()
    /** The reference shades handed to each [extractColors] call, in order. */
    val extractShades = mutableListOf<List<ReferenceShade>>()

    val describeCalls = mutableListOf<String>()
    var describeHandler: suspend (String) -> String? = { path -> "caption:$path" }

    override suspend fun importPhoto(source: String): String {
        imported += source
        return importResults.removeFirst()
    }

    /** Runs before [deleteImage] returns; a test can suspend here to make cleanup slow. */
    var deleteGate: suspend (String) -> Unit = {}

    override suspend fun deleteImage(relativePath: String) {
        deleted += relativePath
        deleteGate(relativePath)
    }

    override suspend fun ensureSegmentationModel() {
        ensureModelCalls++
        ensureModelFailure?.let { throw it }
    }

    override suspend fun removeBackground(relativePath: String): String {
        removeBackgroundCalls += relativePath
        removeBackgroundFailure?.let { throw it }
        return segmentedPath
    }

    /** Runs before [extractColors] returns; a test can suspend here to make extraction late. */
    var extractGate: suspend (String) -> Unit = {}

    override suspend fun extractColors(relativePath: String, shades: List<ReferenceShade>): List<ExtractedColor> {
        extractCalls += relativePath
        extractShades += shades
        extractGate(relativePath)
        return colorsByPath[relativePath].orEmpty()
    }

    override suspend fun describe(relativePath: String): String? {
        describeCalls += relativePath
        return describeHandler(relativePath)
    }
}
