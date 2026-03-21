package com.closet.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A reusable card for displaying clothing items.
 * Parity: This is the native equivalent of components/ClothingCard.tsx.
 * 
 * @param name The name of the clothing item.
 * @param imageModel The image model to load (String, Uri, File, etc.).
 * @param subtitle The subtitle text to display below the name.
 * @param modifier The [Modifier] to be applied to the card.
 * @param onClick Optional click listener.
 */
@Composable
fun ClothingItemCard(
    name: String,
    imageModel: Any?,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = imageModel,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
