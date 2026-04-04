# Chat Enhancement — Actionable Roadmap

Current state: stateless RAG pipeline (encode → top-5 cosine → context block → provider → JSON parse).
Three response types: `Text`, `WithItems`, `WithOutfit`. No conversation memory. No intent routing.

---

## Phase 1 — Multi-turn conversation history

Add a rolling turn history so follow-up messages ("make it more casual", "swap the shoes") resolve against the previous exchange instead of starting cold.

### Data layer

- [x] Add `ConversationTurn(role: Role, text: String)` value class to `core/data/ai/` — `Role` is `enum { User, Assistant }`; keep it provider-agnostic
- [x] Extend `ChatAiProvider.chat()` signature to `chat(userMessage: String, context: String, history: List<ConversationTurn> = emptyList())`
  - Default empty list keeps all four providers compilable before they're each updated
- [x] Update `AnthropicChatProvider`, `OpenAiChatProvider`, `GeminiChatProvider` to map `history` to their respective message-array formats (Anthropic `messages[]`, OpenAI `messages[]`, Gemini `contents[]`)
- [x] `NanoChatProvider` — Nano Prompt API accepts a single string prompt only; prepend the last 2 turns as formatted text inside the prompt rather than using the message array (see pitfalls)

### Repository

- [x] Add `history: List<ConversationTurn>` parameter to `ChatRepository.query()`
- [x] In `ChatRepository`, inject the wardrobe context block **only in the system message** (or first-turn preamble), never re-inject it per subsequent turn — if the context is repeated in every history message, token cost multiplies with turn count

### ViewModel

- [x] Add `private val history = mutableListOf<ConversationTurn>()` in `ChatViewModel`
- [x] On successful response: append user turn + assistant text to `history` — only after success, never on error or cancellation, so failed attempts don't pollute context
- [x] Cap history at **6 turns (3 exchanges)** before passing to repository — older turns are dropped, not stored anywhere persistently
- [x] Add `fun clearChat()` that resets both `_uiState.messages` and `history` — wire to a "New chat" action in `ChatScreen` top bar

### Pitfalls

- **Nano single-prompt constraint**: `NanoChatProvider` receives `history` but must flatten it to `"User: …\nAssistant: …\n"` prefix strings — do not try to call a multi-turn API method that doesn't exist on the Nano Prompt API. The character budget for Nano prompts is tight; cap Nano history at the last 1 exchange (2 turns) rather than 3.
- **Context block repetition**: `ChatRepository.buildContextBlock()` returns the wardrobe items block. With history active, include it once in the first-turn system preamble only. If you include it in every user turn that flows through history, an 8-turn conversation passes the full item list 8 times — guaranteed to hit provider token limits.
- **History committed too early**: Only push turns to `history` inside `onSuccess` in `ChatViewModel.sendMessage()`. Never push on `onFailure` or before the coroutine resolves — if the user retries after an error, the failed attempt must not appear as prior context.

---

## Phase 2 — Intent routing for stat queries

A thin pre-LLM router intercepts questions with unambiguous, data-only answers so they get a direct DAO response instead of going through the full RAG + provider pipeline. Faster, no token cost, works offline.

### Queries to intercept

Start with exactly these three patterns — don't grow this list speculatively:
1. "How many times have I worn [item name]?" → `ClothingDao.getWearCountByName()`
2. "What haven't I worn in [N] days?" / "unworn for [N] days" → `ClothingDao.getItemsNotWornSince()`
3. "What did I wear on [date]?" → `OutfitLogDao.getLogsForDate()`

### Data layer

- [ ] Add `ChatRouter` class in `features/chat/` — takes the raw user message, returns `RouterResult` (either `Routed(response)` or `Unrouted`)
- [ ] Add any missing DAO queries needed (wear count by fuzzy name, items not worn since date, logs for date)
- [ ] `ChatRouter` does **pattern matching only** — no ML, no embeddings. Regex or `contains` on lowercased input. If the pattern doesn't match confidently, return `Unrouted` and fall through to RAG

### Repository

- [ ] `ChatRepository.query()` checks `ChatRouter` first; on `Routed` result, package it into a `ChatResponse` and return early without calling the encoder or provider

### New response type — stat card

- [ ] Add `ChatResponse.WithStat(text: String, label: String, value: String, itemIds: List<Long>)` to `ChatAiProvider.kt`
  - `label`: e.g. "Wear count", `value`: e.g. "14 times"
  - `itemIds`: empty list if the stat is aggregate, populated if it refers to specific items
- [ ] Add `ChatMessage.Assistant.WithStat` mirror in `ChatMessage.kt`
- [ ] Add `StatBubble` composable in `ChatScreen.kt` — compact card with label/value pair and optional item rail

### ViewModel

- [ ] Map `ChatResponse.WithStat` → `ChatMessage.Assistant.WithStat` in `ChatViewModel.toAssistantMessage()`
- [ ] Routed responses do **not** update `history` — they are data answers, not conversational turns; follow-ups on them fall through to RAG naturally

### Pitfalls

- **Fuzzy name matching**: DAO queries need item name matching. Don't do full fuzzy search — use `LIKE '%query%'` and return the closest single match, or return nothing and fall through to RAG if ambiguous. An over-confident router that returns wrong data is worse than no router.
- **Date parsing**: "yesterday", "last Monday", locale differences. Only match ISO-style or unambiguous date strings (e.g. "April 2") in the router; punt everything else to RAG.
- **Don't expand router scope prematurely**: Three patterns is the right starting size. A router that tries to handle 20 patterns becomes a maintenance burden and starts failing subtly on near-miss queries.

---

## Phase 3 — Action intents

The model can embed a suggested next action in its response. The UI surfaces it as a chip row below the message — one tap triggers the action without leaving the chat.

### New response fields

- [ ] Add optional `action: ChatAction?` field to `ChatResponse.WithOutfit` and `ChatResponse.WithItems`
  - `ChatAction` sealed class: `LogOutfit(itemIds)`, `OpenItem(itemId)`, `OpenRecommendations`
  - Keep it as an optional field, not a new response type — actions always accompany content
- [ ] Update `ChatResponseParser` to parse an optional `"action"` object: `{"type":"log_outfit","item_ids":[…]}`
- [ ] Update `ChatPromptPrefix.SYSTEM_PROMPT` to describe the optional `action` field with examples; make it clear the model should only suggest it when the intent is unambiguous

### ViewModel

- [ ] Map `ChatAction` through `ChatMessage` to the UI — actions are stored on the message, not triggered automatically
- [ ] No new ViewModel functions needed — existing `onNavigateToLog`, `onNavigateToItem`, `onNavigateToRecommendations` lambdas cover all three action types

### Screen

- [ ] Add `ActionChipRow` composable below `AssistantBubbleWithItems` and `AssistantBubbleWithOutfit` when an action is present
- [ ] Tapping the chip calls the appropriate existing navigation lambda — no new navigation wiring needed beyond what's already in `ChatScreen`'s parameter list

### Pitfalls

- **Model hallucinating actions**: The model may suggest `log_outfit` on questions that were clearly not outfit queries. Validate on the parse side: `LogOutfit` is only accepted if the parent response is `WithOutfit` with 2–4 item IDs. Reject malformed action objects silently (fall back to no action).
- **Nano + action field**: Nano's structured JSON output is less reliable. The parser's existing graceful fallback (unknown type → `Text`) applies here too, but for actions specifically: treat a missing or malformed `action` block as no action rather than an error. Don't let action parsing failure bubble up to a failed response.
- **Destructive actions**: `LogOutfit` logs items as worn. It must route through the same confirmation dialog that the rest of the app uses — do not log directly from the chip tap. Pass the item IDs to `onNavigateToLog` which opens the existing log confirmation flow.

---

## Phase 4 — Streaming responses (Anthropic / OpenAI / Gemini only)

Replace the current wait-then-render pattern with token-by-token streaming for providers that support it. Nano and the router path remain non-streaming.

### Provider interface

- [ ] Add `chatStream(userMessage: String, context: String, history: List<ConversationTurn>): Flow<StreamChunk>` to `ChatAiProvider`
  - `StreamChunk` sealed class: `Token(text: String)` | `Done(response: ChatResponse)` | `Err(cause: Throwable)`
  - Keep the existing `chat()` non-streaming method — Nano and the router use it; streaming is opt-in
- [ ] Implement streaming in `AnthropicChatProvider` (SSE), `OpenAiChatProvider` (SSE), `GeminiChatProvider` (native stream)
- [ ] `NanoChatProvider` returns `chatStream` as a single-emission flow wrapping the existing `chat()` result — no actual streaming, but satisfies the interface

### ViewModel

- [ ] Replace `chatRepository.query()` call in `sendMessage()` with `chatRepository.queryStream()` which collects `Flow<StreamChunk>`
- [ ] On `Token` chunks: update the `Thinking` placeholder message in-place with accumulated text — use a dedicated `ChatMessage.Assistant.Streaming(partial: String)` type so the UI can distinguish an in-progress stream from a completed `Text` message
- [ ] On `Done`: replace `Streaming` with the final typed `ChatResponse` message, commit to history
- [ ] On `Err`: replace with `Error` bubble, do not commit to history
- [ ] `isLoading` remains true until `Done` or `Err`

### Screen

- [ ] `StreamingBubble(partial: String)` composable — same shape as `AssistantBubble` but appends a blinking cursor character; reuse the existing bubble shape/colors

### Pitfalls

- **JSON accumulation**: The current parser expects a complete JSON object. For streaming, buffer all `Token` chunks and only parse on `Done`. Do not attempt to parse partial JSON mid-stream — it will fail on every intermediate chunk.
- **Flow cancellation**: The stream `Flow` must respect coroutine cancellation. Wrap the streaming HTTP call in `withContext(Dispatchers.IO)` and ensure the HTTP response body is closed in a `finally` block if the coroutine is cancelled. The existing `CancellationException` rethrow pattern applies here.
- **Backpressure on fast providers**: OpenAI/Anthropic can emit tokens faster than Compose can recompose. Debounce or buffer token updates to ~50 ms before posting to `_uiState` to avoid excessive recompositions on long responses.
- **History on streaming**: Only append to `history` after `Done` — same rule as Phase 1. If the user navigates away mid-stream (cancellation), the partial response must not enter history.

---

## Sequencing

| Phase | Depends on | Unlocks |
|-------|-----------|---------|
| 1 — Multi-turn history | nothing | better follow-up quality immediately |
| 2 — Intent routing | nothing (parallel with 1) | faster stat answers, no token cost |
| 3 — Action intents | Phase 1 (history context helps model judge when to suggest actions) | one-tap outfit logging from chat |
| 4 — Streaming | Phase 1 (history parameter already in place) | perceived latency improvement |

Phases 1 and 2 can be built in parallel. Phase 3 should follow Phase 1. Phase 4 is independent of 2 and 3 but easier after Phase 1 has stabilised the provider interface.

---

## Future enhancements

### Phase 2 — ML Kit Entity Extraction for date parsing

The regex date parser in `ChatRouter` intentionally handles only unambiguous patterns (ISO-style dates, simple "April 2" forms). A natural upgrade once Phase 2 is live is replacing it with [ML Kit Entity Extraction](https://developers.google.com/ml-kit/language/entity-extraction), which handles the full range of natural expressions ("yesterday", "last Monday", "3 days ago") on-device with no network call.

**When to consider it:** if analytics or user feedback shows date queries are a heavily-used pattern and the regex router is producing noticeable misses.

**Implementation notes:**
- Uses `com.google.android.gms:play-services-mlkit-entity-extraction` — the traditional Vision ML namespace, not AICore. Works on all devices via Play Services; no Nano or API level restriction.
- Scope to the `full` flavor only; FOSS falls back to the existing strict regex path.
- ~1.5 MB model download via Play Services on first use.
- Drop-in replacement for the date-parsing branch inside `ChatRouter` — no changes needed to the DAO queries or the rest of the router.

### Phase 2 — ML Kit Language Identification as a router guard

The `ChatRouter` pattern-matching is written for English. A non-English query that partially overlaps an English pattern (e.g. a French query containing "worn") could trigger a false-positive match and return wrong data. [ML Kit Language ID](https://developers.google.com/ml-kit/language/identification) can gate the router: if the detected language is not English with sufficient confidence, skip pattern matching entirely and fall through to RAG.

**When to consider it:** if the app ships to non-English locales or if user testing surfaces false-positive router matches on multilingual input.

**Implementation notes:**
- Uses `com.google.mlkit:language-id` — on-device, no network call, ~900 KB model bundled at install time.
- Works on all devices and API levels; no GMS or AICore requirement. Can be added to both `full` and `foss` flavors.
- One call site: a single `languageIdentifier.identifyLanguage(message)` check at the top of `ChatRouter.route()` before any regex is evaluated.
- Threshold suggestion: only proceed with routing if the top language tag is `"en"` with confidence ≥ 0.7; everything else is `Unrouted`.
