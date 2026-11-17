package com.closet.features.outfits

import kotlinx.serialization.Serializable

/**
 * Top-level route for the Journal / wear-history screen.
 *
 * [initialDate] — when non-null, the journal jumps to that date and opens the day detail sheet.
 * Used for deep-linking from the Item Wear History section of [ClothingDetailScreen].
 */
@Serializable
data class JournalRoute(val initialDate: String? = null)

/** Top-level route for the Outfits gallery screen. */
@Serializable
object OutfitsRoute

/**
 * Route for the Outfit Builder screen.
 *
 * [outfitId] = -1 means "create new"; any positive value opens edit mode for that outfit.
 * [preselectedItemIds] seeds the member list on new-outfit creation (e.g. from "Log it" on
 * a recommendation). Ignored in edit mode.
 */
@Serializable
data class OutfitBuilderDestination(
    val outfitId: Long = -1L,
    val preselectedItemIds: List<Long> = emptyList(),
)

/** Route for the Wardrobe Picker screen used to select items while building an outfit. */
@Serializable
object WardrobePickerDestination
