package com.closet.features.outfits

import com.closet.core.ui.resources.ic_symbol_close
import org.jetbrains.compose.resources.painterResource
import com.closet.core.ui.resources.Res as CoreRes
import com.closet.features.outfits.resources.Res
import com.closet.features.outfits.resources.outfits_builder_empty_hint
import com.closet.features.outfits.resources.outfits_builder_remove_item
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.closet.core.ui.components.ClothingItemCard

/**
 * Displays the current outfit members as a simple 2-column grid.
 *
 * SWAP POINT: Replace this composable with `OutfitCanvas` when drag-and-drop
 * canvas layout is implemented. The interface ([members], [onRemoveMember],
 * [resolveImagePath]) should remain stable across the swap.
 */
@Composable
fun OutfitComposition(
    members: List<OutfitMember>,
    onRemoveMember: (Long) -> Unit,
    resolveImagePath: (String?) -> String?,
    modifier: Modifier = Modifier
) {
    if (members.isEmpty()) {
        CompositionEmptyState(modifier = modifier)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(members, key = { it.item.item.id }) { member ->
                MemberCard(
                    member = member,
                    imageModel = resolveImagePath(member.item.item.imagePath),
                    onRemove = { onRemoveMember(member.item.item.id) }
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: OutfitMember,
    imageModel: Any?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        ClothingItemCard(
            name = member.item.item.name,
            imageModel = imageModel,
            subtitle = member.item.category?.name ?: ""
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(CoreRes.drawable.ic_symbol_close),
                contentDescription = stringResource(Res.string.outfits_builder_remove_item),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompositionEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.outfits_builder_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
