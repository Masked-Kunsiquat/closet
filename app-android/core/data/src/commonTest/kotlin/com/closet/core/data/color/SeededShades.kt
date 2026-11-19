package com.closet.core.data.color

import com.closet.core.data.DatabaseSeeder

/**
 * The seeded reference shades as [ReferenceShade]s, built from [DatabaseSeeder.SEEDED_COLORS].
 * Ids follow the seed order (Black = 1 … Teal = 22), as on a fresh install;
 * `ClothingRepositoryColorsTest` checks that against a real database.
 */
internal object SeededShades {
    val names: List<String> = DatabaseSeeder.SEEDED_COLORS.map { it.name }

    val shades: List<ReferenceShade> = DatabaseSeeder.SEEDED_COLORS.flatMapIndexed { i, color ->
        color.shades.map { ReferenceShade(colorId = (i + 1).toLong(), hex = it) }
    }

    fun idOf(name: String): Long {
        val index = names.indexOf(name)
        require(index >= 0) { "No seeded colour '$name'" }
        return (index + 1).toLong()
    }
}
