package com.closet.core.ui.util

import com.closet.core.ui.R

/**
 * Utility to map icon names (from the database) to their drawable resource IDs.
 */
object IconMapper {

    /**
     * Maps an icon name (e.g. from database) to a drawable resource ID.
     */
    fun getIconResource(iconName: String?): Int? = when (iconName?.lowercase()?.replace("-", "_")) {
        "sun", "ic_icon_sun" -> R.drawable.ic_icon_sun
        "leaf", "ic_icon_leaf" -> R.drawable.ic_icon_leaf
        "snowflake", "ic_icon_snowflake" -> R.drawable.ic_icon_snowflake
        "flower", "ic_icon_flower" -> R.drawable.ic_icon_flower
        "thermometer", "ic_icon_thermometer" -> R.drawable.ic_icon_thermometer
        "briefcase", "ic_icon_briefcase" -> R.drawable.ic_icon_briefcase
        "cheers", "ic_icon_cheers" -> R.drawable.ic_icon_cheers
        "couch", "ic_icon_couch" -> R.drawable.ic_icon_couch
        "barbell", "ic_icon_barbell" -> R.drawable.ic_icon_barbell
        "mountains", "ic_icon_mountains" -> R.drawable.ic_icon_mountains
        "t_shirt", "t-shirt", "ic_icon_t_shirt" -> R.drawable.ic_icon_t_shirt
        "pants", "ic_icon_pants" -> R.drawable.ic_icon_pants
        "dress", "ic_icon_dress" -> R.drawable.ic_icon_dress
        "sneaker", "ic_icon_sneaker" -> R.drawable.ic_icon_sneaker
        "handbag", "ic_icon_handbag" -> R.drawable.ic_icon_handbag
        "watch", "ic_icon_watch" -> R.drawable.ic_icon_watch
        "belt", "ic_icon_belt" -> R.drawable.ic_icon_belt
        "sock", "ic_icon_sock" -> R.drawable.ic_icon_sock
        "star", "ic_icon_star" -> R.drawable.ic_icon_star
        "heart", "ic_icon_heart" -> R.drawable.ic_icon_heart
        "coffee", "ic_icon_coffee" -> R.drawable.ic_icon_coffee
        "island", "ic_icon_island" -> R.drawable.ic_icon_island
        "hoodie", "ic_icon_hoodie" -> R.drawable.ic_icon_hoodie
        "goggles", "ic_icon_goggles" -> R.drawable.ic_icon_goggles
        "dresser", "ic_icon_dresser" -> R.drawable.ic_icon_dresser
        "gear_six", "gear", "ic_icon_gear_six" -> R.drawable.ic_icon_gear_six
        "calendar_dots", "ic_icon_calendar_dots" -> R.drawable.ic_icon_calendar_dots
        "chart_bar", "ic_icon_chart_bar" -> R.drawable.ic_icon_chart_bar
        "washing_machine", "ic_icon_washing_machine" -> R.drawable.ic_icon_washing_machine
        "bookmark_simple", "ic_icon_bookmark_simple" -> R.drawable.ic_icon_bookmark_simple
        "crown_simple", "ic_icon_crown_simple" -> R.drawable.ic_icon_crown_simple
        "person_simple_run", "ic_icon_person_simple_run" -> R.drawable.ic_icon_person_simple_run
        "person_simple_swim", "ic_icon_person_simple_swim" -> R.drawable.ic_icon_person_simple_swim
        // Patterns
        "solid", "ic_pattern_solid" -> R.drawable.ic_pattern_solid
        "striped", "ic_pattern_striped" -> R.drawable.ic_pattern_striped
        "plaid_tartan", "plaid/tartan", "ic_pattern_plaid_tartan" -> R.drawable.ic_pattern_plaid_tartan
        "checkered", "ic_pattern_checkered" -> R.drawable.ic_pattern_checkered
        "floral", "ic_pattern_floral" -> R.drawable.ic_pattern_floral
        "geometric", "ic_pattern_geometric" -> R.drawable.ic_pattern_geometric
        "animal_print", "ic_pattern_animal_print" -> R.drawable.ic_pattern_animal_print
        "abstract", "ic_pattern_abstract" -> R.drawable.ic_pattern_abstract
        "tie_dye", "tie-dye", "ic_pattern_tie_dye" -> R.drawable.ic_pattern_tie_dye
        "camouflage", "ic_pattern_camouflage" -> R.drawable.ic_pattern_camouflage
        "paisley", "ic_pattern_paisley" -> R.drawable.ic_pattern_paisley
        "polka_dot", "ic_pattern_polka_dot" -> R.drawable.ic_pattern_polka_dot
        "houndstooth", "ic_pattern_houndstooth" -> R.drawable.ic_pattern_houndstooth
        "graphic", "ic_pattern_graphic" -> R.drawable.ic_pattern_graphic
        "color_block", "ic_pattern_color_block" -> R.drawable.ic_pattern_color_block
        "ombre", "ic_pattern_ombre" -> R.drawable.ic_pattern_ombre
        "other", "ic_pattern_other" -> R.drawable.ic_pattern_other
        else -> null
    }

}
