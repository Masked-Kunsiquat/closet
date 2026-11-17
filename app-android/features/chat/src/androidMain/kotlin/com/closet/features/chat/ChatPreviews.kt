package com.closet.features.chat

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.closet.core.ui.theme.ClosetTheme
import com.closet.features.chat.model.ChatMessage

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Welcome - Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Welcome - Dark")
@Composable
internal fun WelcomePreview() {
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
internal fun TextAnswerPreview() {
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
internal fun ThinkingPreview() {
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
