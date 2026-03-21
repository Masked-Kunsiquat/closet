package com.closet.features.wardrobe

import kotlinx.serialization.Serializable

/**
 * Type-Safe Navigation Destinations for the Wardrobe feature.
 */
@Serializable
object ClosetDestination

@Serializable
data class ClothingDetailDestination(val itemId: Long)

@Serializable
object AddClothingDestination

@Serializable
data class EditClothingDestination(val itemId: Long)

@Serializable
object BrandManagementDestination
