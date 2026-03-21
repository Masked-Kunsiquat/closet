package com.closet.features.wardrobe

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the Wardrobe feature.
 * All destinations are `@Serializable` for use with Compose Navigation 2.9.7+ type-safe routes.
 */

/** Route for the main Closet screen (item grid/list). */
@Serializable
object ClosetDestination

/** Route for the Clothing Detail screen, parameterised by [itemId]. */
@Serializable
data class ClothingDetailDestination(val itemId: Long)

/** Route for the Add Clothing form (no pre-populated item ID). */
@Serializable
object AddClothingDestination

/** Route for the Edit Clothing form, parameterised by [itemId]. */
@Serializable
data class EditClothingDestination(val itemId: Long)

/** Route for the Brand Management screen. */
@Serializable
object BrandManagementDestination
