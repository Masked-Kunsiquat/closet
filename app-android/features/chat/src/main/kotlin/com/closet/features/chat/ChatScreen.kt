package com.closet.features.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.closet.core.data.ai.ChatAction
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.chat.R
import com.closet.features.chat.model.ChatItemSummary
import com.closet.features.chat.model.ChatMessage

// ─── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    onNavigateToItem: (Long) -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToLog: ((List<Long>) -> Unit)?,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatContent(
        uiState = uiState,
        onInputChanged = viewModel::onInputChanged,
        onSendMessage = viewModel::sendMessage,
        onSuggestionSelected = { suggestion ->
            viewModel.onInputChanged(suggestion)
            viewModel.sendMessage()
        },
        onClearChat = viewModel::clearChat,
        onNavigateToItem = onNavigateToItem,
        onNavigateToRecommendations = onNavigateToRecommendations,
        onNavigateToLog = onNavigateToLog,
    )
}

// ─── Content (stateless for previews) ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatContent(
    uiState: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onClearChat: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToLog: ((List<Long>) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val lastMessage = uiState.messages.lastOrNull()
    var showClearDialog by remember { mutableStateOf(false) }

    // Key on the last message object, not just count, so the scroll also fires
    // when the Thinking placeholder is replaced by the real response.
    LaunchedEffect(lastMessage) {
        val lastIndex = uiState.messages.size - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_dialog_title)) },
            text = { Text(stringResource(R.string.chat_clear_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearChat()
                }) {
                    Text(stringResource(R.string.chat_clear_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_clear_dialog_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.chat_powered_by, uiState.providerLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.chat_new_chat),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = uiState.inputText,
                isLoading = uiState.isLoading,
                onInputChanged = onInputChanged,
                onSend = onSendMessage,
            )
        },
    ) { padding ->
        if (uiState.messages.isEmpty()) {
            WelcomeContent(
                isIndexReady = uiState.isIndexReady,
                onSuggestionSelected = onSuggestionSelected,
                modifier = Modifier.padding(padding),
            )
        } else {
            MessageList(
                messages = uiState.messages,
                isIndexReady = uiState.isIndexReady,
                listState = listState,
                onNavigateToItem = onNavigateToItem,
                onNavigateToRecommendations = onNavigateToRecommendations,
                onNavigateToLog = onNavigateToLog,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─── Welcome state ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeContent(
    isIndexReady: Boolean,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chat_welcome_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isIndexReady) R.string.chat_welcome_subtitle_ready
                else R.string.chat_welcome_subtitle_not_ready
            ),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (isIndexReady) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
        Spacer(Modifier.height(24.dp))
        val suggestions = stringArrayResource(R.array.chat_welcome_suggestions)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onSuggestionSelected(suggestion) },
                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}

// ─── Index not-ready banner ────────────────────────────────────────────────────

@Composable
private fun IndexNotReadyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = stringResource(R.string.chat_index_building_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// ─── Message list ──────────────────────────────────────────────────────────────

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isIndexReady: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigateToItem: (Long) -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToLog: ((List<Long>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isIndexReady) {
            item(key = "index_notice") {
                IndexNotReadyBanner()
            }
        }
        itemsIndexed(messages) { _, msg ->
            when (msg) {
                is ChatMessage.User -> UserBubble(msg.text)
                is ChatMessage.Assistant.Text -> AssistantBubble(msg.text)
                is ChatMessage.Assistant.WithItems -> AssistantBubbleWithItems(
                    text = msg.text,
                    items = msg.items,
                    onItemTapped = onNavigateToItem,
                    action = msg.action,
                    onNavigateToLog = onNavigateToLog,
                    onNavigateToRecommendations = onNavigateToRecommendations,
                )
                is ChatMessage.Assistant.WithOutfit -> AssistantBubbleWithOutfit(
                    text = msg.text,
                    items = msg.items,
                    reason = msg.reason,
                    onItemTapped = onNavigateToItem,
                    onLogIt = onNavigateToLog?.let { cb -> { cb(msg.items.map { it.id }) } },
                    onAlternatives = onNavigateToRecommendations,
                    action = msg.action,
                    onNavigateToLog = onNavigateToLog,
                )
                is ChatMessage.Assistant.WithStat -> StatBubble(
                    text = msg.text,
                    label = msg.label,
                    value = msg.value,
                    items = msg.items,
                    onItemTapped = onNavigateToItem,
                )
                is ChatMessage.Assistant.Thinking -> ThinkingBubble()
                is ChatMessage.Assistant.Error -> ErrorBubble(msg.text)
            }
        }
    }
}

// ─── Bubble: user ──────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 4.dp,
                    )
                )
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ─── Bubble: plain assistant ───────────────────────────────────────────────────

@Composable
private fun AssistantBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp,
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Bubble: assistant + item rail ────────────────────────────────────────────

@Composable
private fun AssistantBubbleWithItems(
    text: String,
    items: List<ChatItemSummary>,
    onItemTapped: (Long) -> Unit,
    action: ChatAction? = null,
    onNavigateToLog: ((List<Long>) -> Unit)? = null,
    onNavigateToRecommendations: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistantBubble(text)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(items) { item ->
                ItemChip(item = item, onTap = { onItemTapped(item.id) })
            }
        }
        if (action != null) {
            ActionChipRow(
                action = action,
                onNavigateToLog = onNavigateToLog,
                onNavigateToItem = onItemTapped,
                onNavigateToRecommendations = onNavigateToRecommendations,
            )
        }
    }
}

@Composable
private fun ItemChip(item: ChatItemSummary, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onTap),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageFile != null) {
                AsyncImage(
                    model = item.imageFile,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Checkroom,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
    }
}

// ─── Bubble: assistant + outfit mini-card ─────────────────────────────────────

@Composable
private fun AssistantBubbleWithOutfit(
    text: String,
    items: List<ChatItemSummary>,
    reason: String,
    onItemTapped: (Long) -> Unit,
    onLogIt: (() -> Unit)?,
    onAlternatives: () -> Unit,
    action: ChatAction? = null,
    onNavigateToLog: ((List<Long>) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistantBubble(text)
        OutfitMiniCard(
            items = items,
            reason = reason,
            onItemTapped = onItemTapped,
            onLogIt = onLogIt,
            onAlternatives = onAlternatives,
        )
        if (action != null) {
            ActionChipRow(
                action = action,
                onNavigateToLog = onNavigateToLog,
                onNavigateToItem = onItemTapped,
                onNavigateToRecommendations = onAlternatives,
            )
        }
    }
}

@Composable
private fun OutfitMiniCard(
    items: List<ChatItemSummary>,
    reason: String,
    onItemTapped: (Long) -> Unit,
    onLogIt: (() -> Unit)?,
    onAlternatives: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.92f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutfitImageGrid(
                items = items.take(4),
                onItemTapped = onItemTapped,
                modifier = Modifier.fillMaxWidth(),
            )

            // Stacked item names — same pattern as OutfitComboCard
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // AI reason with AutoAwesome prefix
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onLogIt != null) {
                    OutlinedButton(
                        onClick = onLogIt,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(text = stringResource(R.string.chat_action_log_it), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(onClick = onAlternatives) {
                    Text(
                        text = stringResource(R.string.chat_action_alternatives),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 2-column grid of item images — mirrors [OutfitComboCard]'s [ItemImageGrid] with
 * [BoxWithConstraints] so cells are properly sized squares at any card width.
 */
@Composable
private fun OutfitImageGrid(
    items: List<ChatItemSummary>,
    onItemTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellSpacing = 8.dp
    val rowCount = (items.size + 1) / 2

    BoxWithConstraints(modifier = modifier) {
        val cellSize = (maxWidth - cellSpacing) / 2
        val gridHeight = cellSize * rowCount + cellSpacing * (rowCount - 1).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(gridHeight),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(cellSpacing),
            verticalArrangement = Arrangement.spacedBy(cellSpacing),
            userScrollEnabled = false,
        ) {
            items(items) { item ->
                OutfitImageCell(item = item, onTap = { onItemTapped(item.id) })
            }
        }
    }
}

@Composable
private fun OutfitImageCell(
    item: ChatItemSummary,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (item.imageFile != null) {
            AsyncImage(
                model = item.imageFile,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = item.name,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

// ─── Action chip row ──────────────────────────────────────────────────────────

/**
 * A single [AssistChip] surfacing the model's suggested next action.
 * Rendered below the message bubble — one tap triggers the action without leaving chat.
 * Tapping [ChatAction.LogOutfit] routes through the existing log confirmation flow via [onNavigateToLog].
 */
@Composable
private fun ActionChipRow(
    action: ChatAction,
    onNavigateToLog: ((List<Long>) -> Unit)?,
    onNavigateToItem: (Long) -> Unit,
    onNavigateToRecommendations: () -> Unit,
) {
    val (label, icon, onClick) = when (action) {
        is ChatAction.LogOutfit -> Triple(
            stringResource(R.string.chat_action_chip_log_outfit),
            Icons.Default.Checkroom,
            { onNavigateToLog?.invoke(action.itemIds) ?: Unit },
        )
        is ChatAction.OpenItem -> Triple(
            stringResource(R.string.chat_action_chip_view_item),
            Icons.Default.Checkroom,
            { onNavigateToItem(action.itemId) },
        )
        is ChatAction.OpenRecommendations -> Triple(
            stringResource(R.string.chat_action_chip_explore_outfits),
            Icons.Default.AutoAwesome,
            onNavigateToRecommendations,
        )
    }
    Row {
        AssistChip(
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

// ─── Bubble: stat card ────────────────────────────────────────────────────────

@Composable
private fun StatBubble(
    text: String,
    label: String,
    value: String,
    items: List<ChatItemSummary>,
    onItemTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistantBubble(text)
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Label / value row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Optional item rail — only shown when items are present
                if (items.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(items) { item ->
                            ItemChip(item = item, onTap = { onItemTapped(item.id) })
                        }
                    }
                }
            }
        }
    }
}

// ─── Bubble: thinking indicator ───────────────────────────────────────────────

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp,
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = when (index) {
                                        0 -> 0.9f
                                        1 -> 0.55f
                                        else -> 0.25f
                                    }
                                )
                            ),
                    )
                }
            }
        }
    }
}

// ─── Bubble: error ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ─── Input bar ────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    inputText: String,
    isLoading: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.chat_send_button_description),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Welcome - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Welcome - Dark")
@Composable
private fun WelcomePreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatContent(
                uiState = ChatUiState(),
                onInputChanged = {},
                onSendMessage = {},
                onSuggestionSelected = {},
                onClearChat = {},
                onNavigateToItem = {},
                onNavigateToRecommendations = {},
                onNavigateToLog = null,
            )
        }
    }
}

@Preview(showBackground = true, name = "Text Answer - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TextAnswerPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatContent(
                uiState = ChatUiState(
                    messages = listOf(
                        ChatMessage.User("How many times have I worn my navy chinos?"),
                        ChatMessage.Assistant.Text(
                            "Your Navy Chinos have been worn 14 times. Last worn 19 days ago."
                        ),
                    ),
                    providerLabel = "Claude",
                ),
                onInputChanged = {},
                onSendMessage = {},
                onSuggestionSelected = {},
                onClearChat = {},
                onNavigateToItem = {},
                onNavigateToRecommendations = {},
                onNavigateToLog = null,
            )
        }
    }
}

@Preview(showBackground = true, name = "Thinking - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThinkingPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatContent(
                uiState = ChatUiState(
                    messages = listOf(
                        ChatMessage.User("Suggest something for a weekend brunch"),
                        ChatMessage.Assistant.Thinking,
                    ),
                    isLoading = true,
                ),
                onInputChanged = {},
                onSendMessage = {},
                onSuggestionSelected = {},
                onClearChat = {},
                onNavigateToItem = {},
                onNavigateToRecommendations = {},
                onNavigateToLog = null,
            )
        }
    }
}
