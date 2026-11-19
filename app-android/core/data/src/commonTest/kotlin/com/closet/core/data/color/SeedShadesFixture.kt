package com.closet.core.data.color

/** Temporary copy of the seed shade table; Task 3 replaces it with DatabaseSeeder.SEEDED_COLORS. */
internal object SeedShadesFixture {
    private val table: List<List<String>> = listOf(
        listOf("#000000", "#1A1A1A", "#2B2B2B"), // 1 Black
        listOf("#FFFFFF", "#F4F4F2", "#EDEDE8"), // 2 White
        listOf("#808080", "#A0A0A0", "#6E6E6E", "#BEBEBE"), // 3 Grey
        listOf("#F5F5DC", "#E8DCC4", "#D9C8A9"), // 4 Beige
        listOf("#000080", "#1F2A44", "#202A5A", "#2C3E63"), // 5 Navy
        listOf("#FF0000", "#B3202A", "#D62B2B", "#C41E3A"), // 6 Red
        listOf("#4169E1", "#2E50A8", "#3A5FCD"), // 7 Royal Blue
        listOf("#87CEEB", "#8DB8DE", "#A7C7E7"), // 8 Sky Blue
        listOf("#228B22", "#2E5E3A", "#355E3B", "#4F7942"), // 9 Forest Green
        listOf("#808000", "#6B6B3A", "#708238"), // 10 Olive
        listOf("#98FF98", "#A8DCC0", "#B2E0C8"), // 11 Mint
        listOf("#800020", "#6D1A2B", "#7B2D3A"), // 12 Burgundy
        listOf("#FFC0CB", "#F2A7B8", "#E75480", "#F4C2C2"), // 13 Pink
        listOf("#FFA500", "#E8742A", "#F28C28", "#FF7F50"), // 14 Orange
        listOf("#FFFF00", "#F2CF3A", "#FFFACD", "#FFF59D", "#E1AD01", "#FFD300", "#F6E7A5"), // 15 Yellow
        listOf("#800080", "#6A3D8F", "#9B59B6", "#B57EDC", "#CDB4E0", "#E6E6FA"), // 16 Purple
        listOf("#6B4423", "#5C4033", "#7B5B3A", "#4E342E"), // 17 Brown
        listOf("#D2B48C", "#C8A47E", "#C3B091", "#C19A6B"), // 18 Tan
        listOf("#36454F", "#3C3C3C", "#4A4A4A"), // 19 Charcoal
        listOf("#FFFDD0", "#F3EBD3", "#FAF3E0", "#FFF5DC"), // 20 Cream
        listOf("#3B5A80", "#4F6D8F", "#5B7BA6", "#2F4A6B"), // 21 Denim
        listOf("#008080", "#1F7A7A", "#2A9D8F"), // 22 Teal
    )

    val names: List<String> = listOf(
        "Black", "White", "Grey", "Beige", "Navy", "Red", "Royal Blue", "Sky Blue",
        "Forest Green", "Olive", "Mint", "Burgundy", "Pink", "Orange", "Yellow", "Purple",
        "Brown", "Tan", "Charcoal", "Cream", "Denim", "Teal",
    )

    val shades: List<ReferenceShade> = table.flatMapIndexed { i, hexes ->
        hexes.map { ReferenceShade(colorId = (i + 1).toLong(), hex = it) }
    }

    fun idOf(name: String): Long = (names.indexOf(name) + 1).toLong()
}
