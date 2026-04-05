# Chat Enhancement — Test Coverage Roadmap

Tests for the completed phases of `chat-enhance-roadmap.md` (Phases 1–3).  
Target: **0.7.1**. No new Gradle dependencies required — MockK, Turbine, and `coroutines-test` are already in `libs.versions.toml`.

---

## Files to create

```
core/data/src/test/kotlin/com/closet/core/data/ai/
    ChatResponseParserTest.kt          ✓ done

features/chat/src/test/kotlin/com/closet/features/chat/
    RegexDateParserTest.kt
    ChatViewModelTest.kt
    ChatRouterPatternTest.kt
```

---

## 1 — `ChatResponseParserTest`

Pure logic, no Android or GMS dependencies. Highest value per line of test code.

### JSON extraction (`extractJson`)

- [x] Plain `{…}` — returned as-is
- [x] Markdown-fenced ` ```json\n{…}\n``` ` — inner block extracted
- [x] Markdown fence without `json` tag — inner block extracted
- [x] Prose with embedded `{…}` — outermost braces extracted
- [x] No `{…}` found — original string returned (parse failure surfaced by caller)

### `text` type

- [x] Valid `{"type":"text","text":"hello"}` → `ChatResponse.Text("hello")`
- [x] Missing `text` field → `Result.failure`
- [x] Missing `type` field → `Result.failure`

### `items` type

- [x] Valid with `item_ids` → `ChatResponse.WithItems` with correct IDs
- [x] Missing `item_ids` → `Result.failure`
- [x] Empty `item_ids` → `Result.failure`
- [x] Non-long entry in `item_ids` → `Result.failure`

### `outfit` type

- [x] Valid with 2–4 IDs and `reason` → `ChatResponse.WithOutfit`
- [x] 2 IDs — valid lower bound
- [x] 4 IDs — valid upper bound
- [x] 1 ID → `Result.failure` (too few)
- [x] 5 IDs → `Result.failure` (too many)
- [x] Missing `reason` → `Result.failure`
- [x] Blank `reason` → `Result.failure`
- [x] Missing `item_ids` → `Result.failure`

### Unknown type

- [x] `{"type":"unknown","text":"hi"}` → `ChatResponse.Text("hi")` (graceful fallback)
- [x] Future type `"widget"` with valid `text` → `ChatResponse.Text`

### Action parsing — `log_outfit`

- [x] Accepted on `outfit` parent with matching IDs → `ChatAction.LogOutfit`
- [x] Rejected on `items` parent → `action = null`, parent response still succeeds
- [x] 1 ID → `action = null`
- [x] 5 IDs (valid parent of 4) → `action = null`
- [x] IDs not all present in parent response → `action = null` *(added by parseAction hardening)*
- [x] Non-long element in `item_ids` → `action = null` *(replaces silent mapNotNull drop)*
- [x] Non-positive ID (0) → `action = null` *(added by parseAction hardening)*
- [x] Missing `item_ids` in action block → `action = null`

### Action parsing — `open_item`

- [x] Valid `item_id` present in parent → `ChatAction.OpenItem(id)`
- [x] Works on `outfit` parent as well as `items`
- [x] Missing `item_id` → `action = null`
- [x] `item_id` not in parent → `action = null` *(added by parseAction hardening)*
- [x] Non-positive `item_id` (0) → `action = null` *(added by parseAction hardening)*
  - Note: a string `item_id` is also implicitly covered — `longOrNull` on a JSON string returns null

### Action parsing — `open_recommendations`

- [x] `{"type":"open_recommendations"}` → `ChatAction.OpenRecommendations`
- [x] Accepted on both `items` and `outfit` parent types

### Action parsing — error isolation

- [x] Unknown action type → `action = null`, parent response still succeeds
- [x] Malformed action block (missing `type`) → `action = null`, parent still succeeds
- [x] Missing `action` field entirely → `action = null`

---

## 2 — `RegexDateParserTest`

`regexParseDate()` is an `internal fun` in `RegexDateParser.kt` — no mocking needed.

### ISO dates

- [x] `"2026-04-04"` → `"2026-04-04"`
- [x] ISO date embedded in a sentence → extracted correctly
- [x] `"2026-13-01"` → `"2026-13-01"` (ISO path is a regex match, not a date validator — invalid calendar dates are not caught here)

### Month Day patterns

- [x] `"April 4"` → `"<currentYear>-04-04"` (current year assumed; use `LocalDate.now().year` in assertion)
- [x] `"April 4th"` → `"<currentYear>-04-04"` (ordinal suffix stripped)
- [x] `"Apr 4"` → `"<currentYear>-04-04"` (abbreviated month)
- [x] `"april 4, 2025"` → `"2025-04-04"` (explicit year used)
- [x] Input is case-insensitive (`"APRIL 4"` → correct result)
- [x] All 12 full month names → correct month number
- [x] All abbreviated month names → correct month number (note: `may` has no distinct abbreviation)
- [x] `"April 31"` → `null` (invalid day for month)

### Unhandled patterns (must return `null`)

- [x] `"yesterday"`
- [x] `"last Monday"`
- [x] `"3 days ago"`
- [x] Blank string
- [x] Garbage input `"wear count"`

---

## 3 — `ChatViewModelTest`

Follows the `StatsViewModelTest` pattern: MockK + Turbine + `UnconfinedTestDispatcher`.

Fake `ChatRepository` with a `mockk` that returns configurable `Result<ChatResponse>`.  
Fake `ClothingDao.getItemDetailsByIds()` returning empty list (no image lookups needed).  
Fake `StorageRepository` and `EmbeddingIndex` as no-ops.

### Message flow

- [ ] `sendMessage()` with blank input → no state change, no repository call
- [ ] `sendMessage()` while `isLoading = true` → no-op (second call ignored)
- [ ] Successful response → `Thinking` placeholder replaced by assistant message; `isLoading = false`
- [ ] Failed response → `Thinking` replaced by `ChatMessage.Assistant.Error`; `isLoading = false`
- [ ] Failed response → `isLoading = false` (loading not stuck)

### History — Phase 1

- [ ] Successful `Text` response → `history` gains 2 turns (user + assistant)
- [ ] Failed response → `history` unchanged
- [ ] After 3 successful exchanges → history has 6 turns
- [ ] After 4th successful exchange → history still 6 turns (oldest pair dropped)
- [ ] History snapshot taken before launch (in-flight message not in snapshot)

### History — Phase 2 stat responses

- [ ] `WithStat` response → `history` **not** updated
- [ ] Follow-up after a stat → history still reflects only non-stat turns

### `clearChat()`

- [ ] `messages` list reset to empty
- [ ] `history` reset (subsequent send passes empty history to repository)
- [ ] `inputText` cleared

### `toAssistantMessage()` mapping

- [ ] `ChatResponse.Text` → `ChatMessage.Assistant.Text`
- [ ] `ChatResponse.WithItems` with action → `ChatMessage.Assistant.WithItems` with action preserved
- [ ] `ChatResponse.WithOutfit` with action → `ChatMessage.Assistant.WithOutfit` with action preserved
- [ ] `ChatResponse.WithStat` → `ChatMessage.Assistant.WithStat`

---

## 4 — `ChatRouterPatternTest`

The full-flavor `ChatRouter` calls `LanguageIdentification.getClient()` at construction time, which requires GMS. Two layers to test without instrumented setup:

**Layer A — FOSS stub**: trivial but worth having as a smoke test.

- [ ] FOSS `ChatRouter.route(anyString)` always returns `Unrouted`

**Layer B — Regex/pattern helpers** (full flavor): `ITEM_NAME_PATTERN`, `DAYS_PATTERN`, and `WORE_ON_INTERROGATIVE_PATTERN` are `internal val` in `ChatRouter`'s companion object, so they are directly accessible from unit tests in the same module (`features/chat/src/test/`). Test them without constructing `ChatRouter`.

### `ITEM_NAME_PATTERN`

- [ ] `"how many times have i worn my grey blazer"` → captures `"grey blazer"`
- [ ] `"how many times worn my black jeans?"` → captures `"black jeans"`
- [ ] `"worn the white shirt"` → captures `"white shirt"`
- [ ] `"wear count for the white shirt"` → no match (no "worn"/"how many times worn" prefix; `extractItemName` returns null, falls through to RAG)
- [ ] Query with no item name → no match

### `DAYS_PATTERN`

- [ ] `"30 days"` → count 30, unit days → 30
- [ ] `"2 weeks"` → count 2, unit weeks → 14
- [ ] `"1 week"` → 7
- [ ] `"lately"` → no match (falls back to `DEFAULT_UNWORN_DAYS`)

### `WORE_ON_INTERROGATIVE_PATTERN`

- [ ] `"what did i wore on tuesday"` → matches
- [ ] `"what goes with what i wore on tuesday"` — gap between `what` and `i` is > 15 chars → no match (false-positive guard)

### Pattern priority (never-worn before not-worn-since)

- [ ] `"what have i never worn"` matches `matchesNeverWorn`, not `matchesNotWornSince`
  - Verify by checking only `matchesNeverWorn` returns true and `matchesNotWornSince` returns false for this input

---

## Pitfalls

- **`LanguageIdentification.getClient()` in `ChatRouter` constructor** — do not instantiate full-flavor `ChatRouter` in unit tests. Test patterns via the `internal` companion vals (`ChatRouter.ITEM_NAME_PATTERN`, etc.) and test routing behaviour through `ChatRepository` with a mocked router instead.
- **`embeddingIndex.size`** — `ChatViewModel` reads this at construction; stub it to return `> 0` to put the UI into the ready state by default.
- **History is `private`** — assert indirectly: after a known number of sends, check the `history` list passed to the mock repository on the next `sendMessage()` call via `verify { repo.query(any(), capture(slot)) }`.
