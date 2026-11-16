package com.closet.core.ui.util

import com.closet.core.ui.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Utility to map icon names (from the database) to their drawable resources.
 */
object IconMapper {

    /**
     * Maps an icon name (e.g. from database) to a drawable resource.
     */
    fun getIconResource(iconName: String?): DrawableResource? = when (iconName?.lowercase()?.replace("-", "_")) {
        "sun", "ic_icon_sun" -> Res.drawable.ic_icon_sun
        "leaf", "ic_icon_leaf" -> Res.drawable.ic_icon_leaf
        "snowflake", "ic_icon_snowflake" -> Res.drawable.ic_icon_snowflake
        "flower", "ic_icon_flower" -> Res.drawable.ic_icon_flower
        "thermometer", "ic_icon_thermometer" -> Res.drawable.ic_icon_thermometer
        "briefcase", "ic_icon_briefcase" -> Res.drawable.ic_icon_briefcase
        "cheers", "ic_icon_cheers" -> Res.drawable.ic_icon_cheers
        "couch", "ic_icon_couch" -> Res.drawable.ic_icon_couch
        "barbell", "ic_icon_barbell" -> Res.drawable.ic_icon_barbell
        "mountains", "ic_icon_mountains" -> Res.drawable.ic_icon_mountains
        "t_shirt", "t-shirt", "ic_icon_t_shirt" -> Res.drawable.ic_icon_t_shirt
        "pants", "ic_icon_pants" -> Res.drawable.ic_icon_pants
        "dress", "ic_icon_dress" -> Res.drawable.ic_icon_dress
        "sneaker", "ic_icon_sneaker" -> Res.drawable.ic_icon_sneaker
        "handbag", "ic_icon_handbag" -> Res.drawable.ic_icon_handbag
        "watch", "ic_icon_watch" -> Res.drawable.ic_icon_watch
        "belt", "ic_icon_belt" -> Res.drawable.ic_icon_belt
        "sock", "ic_icon_sock" -> Res.drawable.ic_icon_sock
        "star", "ic_icon_star" -> Res.drawable.ic_icon_star
        "heart", "ic_icon_heart" -> Res.drawable.ic_icon_heart
        "coffee", "ic_icon_coffee" -> Res.drawable.ic_icon_coffee
        "island", "ic_icon_island" -> Res.drawable.ic_icon_island
        "hoodie", "ic_icon_hoodie" -> Res.drawable.ic_icon_hoodie
        "goggles", "ic_icon_goggles" -> Res.drawable.ic_icon_goggles
        "dresser", "ic_icon_dresser" -> Res.drawable.ic_icon_dresser
        "gear_six", "gear", "ic_icon_gear_six" -> Res.drawable.ic_icon_gear_six
        "calendar_dots", "ic_icon_calendar_dots" -> Res.drawable.ic_icon_calendar_dots
        "chart_bar", "ic_icon_chart_bar" -> Res.drawable.ic_icon_chart_bar
        "washing_machine", "ic_icon_washing_machine" -> Res.drawable.ic_icon_washing_machine
        "bookmark_simple", "ic_icon_bookmark_simple" -> Res.drawable.ic_icon_bookmark_simple
        "crown_simple", "ic_icon_crown_simple" -> Res.drawable.ic_icon_crown_simple
        "person_simple_run", "ic_icon_person_simple_run" -> Res.drawable.ic_icon_person_simple_run
        "person_simple_swim", "ic_icon_person_simple_swim" -> Res.drawable.ic_icon_person_simple_swim
        // Patterns
        "solid", "ic_pattern_solid" -> Res.drawable.ic_pattern_solid
        "striped", "ic_pattern_striped" -> Res.drawable.ic_pattern_striped
        "plaid_tartan", "plaid/tartan", "ic_pattern_plaid_tartan" -> Res.drawable.ic_pattern_plaid_tartan
        "checkered", "ic_pattern_checkered" -> Res.drawable.ic_pattern_checkered
        "floral", "ic_pattern_floral" -> Res.drawable.ic_pattern_floral
        "geometric", "ic_pattern_geometric" -> Res.drawable.ic_pattern_geometric
        "animal_print", "ic_pattern_animal_print" -> Res.drawable.ic_pattern_animal_print
        "abstract", "ic_pattern_abstract" -> Res.drawable.ic_pattern_abstract
        "tie_dye", "tie-dye", "ic_pattern_tie_dye" -> Res.drawable.ic_pattern_tie_dye
        "camouflage", "ic_pattern_camouflage" -> Res.drawable.ic_pattern_camouflage
        "paisley", "ic_pattern_paisley" -> Res.drawable.ic_pattern_paisley
        "polka_dot", "ic_pattern_polka_dot" -> Res.drawable.ic_pattern_polka_dot
        "houndstooth", "ic_pattern_houndstooth" -> Res.drawable.ic_pattern_houndstooth
        "graphic", "ic_pattern_graphic" -> Res.drawable.ic_pattern_graphic
        "color_block", "ic_pattern_color_block" -> Res.drawable.ic_pattern_color_block
        "ombre", "ic_pattern_ombre" -> Res.drawable.ic_pattern_ombre
        "other", "ic_pattern_other" -> Res.drawable.ic_pattern_other
        else -> null
    }

}
