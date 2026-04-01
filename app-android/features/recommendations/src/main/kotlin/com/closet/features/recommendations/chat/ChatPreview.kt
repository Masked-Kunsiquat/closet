package com.closet.features.recommendations.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.closet.core.ui.theme.ClosetTheme

// ─── Local preview-only data model ────────────────────────────────────────────

private data class ChatItem(val name: String, val colorIndex: Int)

private data class ChatOutfit(
    val items: List<ChatItem>,
    val reason: String,
)

private sealed interface ChatMsg {
    data class User(val text: String) : ChatMsg
    sealed interface Assistant : ChatMsg {
        data class Plain(val text: String) : Assistant
        data class WithItems(val text: String, val items: List<ChatItem>) : Assistant
        data class WithOutfit(val text: String, val outfit: ChatOutfit) : Assistant
        data object Thinking : Assistant
    }
}

// ─── Sample preview conversations ─────────────────────────────────────────────

private val welcomeSuggestions = listOf(
    "What should I wear tonight?",
    "What haven't I worn lately?",
    "What goes with my grey blazer?",
    "Show me a work outfit",
)

private val textOnlyConvo = listOf(
    ChatMsg.User("How many times have I worn my navy chinos?"),
    ChatMsg.Assistant.Plain(
        "Your Navy Chinos have been worn 14 times. Last worn 19 days ago, on March 12th. " +
            "They're one of your 5 most-worn items this year."
    ),
)

private val itemRailConvo = listOf(
    ChatMsg.User("What haven't I worn in over a month?"),
    ChatMsg.Assistant.WithItems(
        text = "These 5 items haven't been worn in over 30 days:",
        items = listOf(
            ChatItem("Burgundy Sweater", 0),
            ChatItem("Linen Trousers", 1),
            ChatItem("White Sneakers", 2),
            ChatItem("Denim Jacket", 3),
            ChatItem("Striped Tee", 0),
        ),
    ),
)

private val outfitConvo = listOf(
    ChatMsg.User("Something for a casual dinner tonight?"),
    ChatMsg.Assistant.WithOutfit(
        text = "Here's something that works well for a casual dinner:",
        outfit = ChatOutfit(
            items = listOf(
                ChatItem("Navy Blazer", 3),
                ChatItem("White Tee", 2),
                ChatItem("Chinos", 1),
                ChatItem("Brown Loafers", 0),
            ),
            reason = "The blazer elevates it without being formal, and the chinos keep the vibe relaxed.",
        ),
    ),
)

private val thinkingConvo = listOf(
    ChatMsg.User("Suggest something for a weekend brunch"),
    ChatMsg.Assistant.Thinking,
)

// ─── Placeholder colors ────────────────────────────────────────────────────────

@Composable
private fun placeholderColor(index: Int): Color = when (index % 4) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    2 -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun placeholderOnColor(index: Int): Color = when (index % 4) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    2 -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    messages: List<ChatMsg>,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Wardrobe Assistant",
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
                                text = "Powered by AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { ChatInputBar() },
        modifier = modifier,
    ) { padding ->
        if (messages.isEmpty()) {
            WelcomeContent(modifier = Modifier.padding(padding))
        } else {
            MessageList(
                messages = messages,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─── Welcome state ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeContent(modifier: Modifier = Modifier) {
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
            text = "Ask me anything about\nyour wardrobe",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "I know what you own, what you've worn,\nand what works together.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            welcomeSuggestions.forEach { suggestion ->
                AssistChip(
                    onClick = {},
                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}

// ─── Message list ──────────────────────────────────────────────────────────────

@Composable
private fun MessageList(
    messages: List<ChatMsg>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        messages.forEach { msg ->
            when (msg) {
                is ChatMsg.User -> UserBubble(msg.text)
                is ChatMsg.Assistant.Plain -> AssistantBubble(msg.text)
                is ChatMsg.Assistant.WithItems -> AssistantBubbleWithItems(msg.text, msg.items)
                is ChatMsg.Assistant.WithOutfit -> AssistantBubbleWithOutfit(msg.text, msg.outfit)
                is ChatMsg.Assistant.Thinking -> ThinkingBubble()
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
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp,
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
private fun AssistantBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
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
private fun AssistantBubbleWithItems(text: String, items: List<ChatItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistantBubble(text)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
        ) {
            items(items) { item ->
                ItemChip(item)
            }
        }
    }
}

@Composable
private fun ItemChip(item: ChatItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(placeholderColor(item.colorIndex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = placeholderOnColor(item.colorIndex).copy(alpha = 0.6f),
            )
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
private fun AssistantBubbleWithOutfit(text: String, outfit: ChatOutfit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistantBubble(text)
        OutfitMiniCard(outfit)
    }
}

@Composable
private fun OutfitMiniCard(outfit: ChatOutfit) {
    Card(
        modifier = Modifier.fillMaxWidth(0.92f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 2×2 grid — two Rows of two cells each
            val rows = outfit.items.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { item ->
                            OutfitImageCell(
                                item = item,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Pad to 2 columns if last row has 1 item
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Item names
            Text(
                text = outfit.items.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // AI reason
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = outfit.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Inline actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {},
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp,
                    ),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = "Log it",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = {}) {
                    Text(
                        text = "Alternatives →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitImageCell(item: ChatItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(placeholderColor(item.colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Checkroom,
            contentDescription = item.name,
            modifier = Modifier.size(24.dp),
            tint = placeholderOnColor(item.colorIndex).copy(alpha = 0.5f),
        )
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
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
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

// ─── Input bar ────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar() {
    Surface(
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = {
                    Text(
                        text = "Ask about your wardrobe…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = {},
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Welcome - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Welcome - Dark",
)
@Composable
private fun WelcomePreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(messages = emptyList())
        }
    }
}

@Preview(showBackground = true, name = "Text Answer - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Text Answer - Dark",
)
@Composable
private fun TextAnswerPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(messages = textOnlyConvo)
        }
    }
}

@Preview(showBackground = true, name = "Item Rail - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Item Rail - Dark",
)
@Composable
private fun ItemRailPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(messages = itemRailConvo)
        }
    }
}

@Preview(showBackground = true, name = "Outfit Card - Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Outfit Card - Dark",
)
@Composable
private fun OutfitCardPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(messages = outfitConvo)
        }
    }
}

@Preview(showBackground = true, name = "Thinking - Light")
@Composable
private fun ThinkingPreview() {
    ClosetTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatScreen(messages = thinkingConvo)
        }
    }
}
