# Chat Enhancement — Test Coverage Roadmap

Tests for the completed phases of `chat-enhance-roadmap.md` (Phases 1–3).  
Target: **0.7.1**. No new Gradle dependencies required — MockK, Turbine, and `coroutines-test` are already in `libs.versions.toml`.

---

## Files to create

```
core/data/src/test/kotlin/com/closet/core/data/ai/
    ChatResponseParserTest.kt          ✓ done

features/chat/src/test/kotlin/com/closet/features/chat/
    RegexDateParserTest.kt             ✓ done
    ChatViewModelTest.kt               ✓ done

# Flavor-specific unit test source sets (AGP prefix convention: src/test<Flavor>/)
features/chat/src/testFoss/kotlin/com/closet/features/chat/
    ChatRouterFossTest.kt              ✓ done  (FOSS stub smoke test)

features/chat/src/testFull/kotlin/com/closet/features/chat/
    ChatRouterPatternTest.kt           ✓ done  (tests ChatRouterPatterns internal object)
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

### Month-Day patterns

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

- [x] `sendMessage()` with blank input → no state change, no repository call
- [x] `sendMessage()` while `isLoading = true` → no-op (second call ignored)
- [x] Successful response → `Thinking` placeholder replaced by assistant message; `isLoading = false`
- [x] Failed response → `Thinking` replaced by `ChatMessage.Assistant.Error`; `isLoading = false`
- [x] Failed response → `isLoading = false` (loading not stuck)

### History — Phase 1

- [x] Successful `Text` response → `history` gains 2 turns (user + assistant)
- [x] Failed response → `history` unchanged
- [x] After 3 successful exchanges → history has 6 turns
- [x] After 4th successful exchange → history still 6 turns (oldest pair dropped)
- [x] History snapshot taken before launch (in-flight message not in snapshot)

### History — Phase 2 stat responses

- [x] `WithStat` response → `history` **not** updated
- [x] Follow-up after a stat → history still reflects only non-stat turns

### `clearChat()`

- [x] `messages` list reset to empty
- [x] `history` reset (subsequent send passes empty history to repository)
- [x] `inputText` cleared

### `toAssistantMessage()` mapping

- [x] `ChatResponse.Text` → `ChatMessage.Assistant.Text`
- [x] `ChatResponse.WithItems` with action → `ChatMessage.Assistant.WithItems` with action preserved
- [x] `ChatResponse.WithOutfit` with action → `ChatMessage.Assistant.WithOutfit` with action preserved
- [x] `ChatResponse.WithStat` → `ChatMessage.Assistant.WithStat`

---

## 4 — `ChatRouterPatternTest`

The full-flavor `ChatRouter` calls `LanguageIdentification.getClient()` at construction time, which requires GMS. Two layers to test without instrumented setup:

**Layer A — FOSS stub**: trivial but worth having as a smoke test. Lives in `src/testFoss/` (foss-specific unit test source set).

- [x] FOSS `ChatRouter.route(anyString)` always returns `Unrouted`

**Layer B — Regex/pattern helpers** (full flavor): `ITEM_NAME_PATTERN`, `DAYS_PATTERN`, and `WORE_ON_INTERROGATIVE_PATTERN` are `internal val` in `ChatRouterPatterns`. Tests live in `src/testFull/` (full-specific unit test source set — `src/test/` compiles for both flavors and the FOSS `ChatRouter` has no companion object).

> **Regex fix applied**: `WORE_ON_INTERROGATIVE_PATTERN` was anchored with `^` to prevent `containsMatchIn` from matching on a mid-sentence "what" (e.g., `"what goes with what i wore on tuesday"`). Without the anchor the false-positive guard was broken.

### `ITEM_NAME_PATTERN`

- [x] `"how many times have i worn my grey blazer"` → captures `"grey blazer"`
- [x] `"how many times worn my black jeans?"` → captures `"black jeans"`
- [x] `"worn the white shirt"` → captures `"white shirt"`
- [x] `"wear count for the white shirt"` → captures `"white shirt"` (wear-count construction)
- [x] `"how many times did i wear my blazer"` → captures `"blazer"` (present-tense wear)
- [x] Query with no item name → no match

### `DAYS_PATTERN`

- [x] `"30 days"` → count 30, unit days → 30
- [x] `"2 weeks"` → count 2, unit weeks → 14
- [x] `"1 week"` → 7
- [x] `"lately"` → no match (falls back to `DEFAULT_UNWORN_DAYS`)
- [x] `"5 weekdays"` → no match (trailing `\b` prevents compound-word false positive)

### `WORE_ON_INTERROGATIVE_PATTERN`

- [x] `"what did i wore on tuesday"` → matches
- [x] `"what goes with what i wore on tuesday"` — gap between `what` and `i` is > 15 chars → no match (false-positive guard)

### Pattern priority (never-worn before not-worn-since)

- [x] `"what have i never worn"` matches `matchesNeverWorn`, not `matchesNotWornSince`
  - Verify by checking only `matchesNeverWorn` returns true and `matchesNotWornSince` returns false for this input
- [x] `"never wear white after labor day"` → `matchesNeverWorn` returns false (no past-tense form; broad `never`+`wear` branch tightened to regex requiring worn/wore/tried on)

---

## Pitfalls

- **`LanguageIdentification.getClient()` in `ChatRouter` constructor** — do not instantiate full-flavor `ChatRouter` in unit tests. Test patterns via the `internal` companion vals (`ChatRouter.ITEM_NAME_PATTERN`, etc.) and test routing behaviour through `ChatRepository` with a mocked router instead.
- **`embeddingIndex.size`** — `ChatViewModel` reads this at construction; stub it to return `> 0` to put the UI into the ready state by default.
- **History is `private`** — assert indirectly: after a known number of sends, check the `history` list passed to the mock repository on the next `sendMessage()` call via `verify { repo.query(any(), capture(slot)) }`.
- **Flavor-specific unit test source sets** — AGP uses `src/testFull/` and `src/testFoss/` (NOT `src/fullTest/` / `src/fossTest/`) for flavor-scoped unit tests. Tasks: `testFullDebugUnitTest` / `testFossDebugUnitTest`.
- **`stateIn(WhileSubscribed)` + Turbine `awaitItem()` race (resolved)** — `WhileSubscribed` doesn't start the upstream until a subscriber appears. Inside `runTest`, this launch is queued in the scheduler; if `sendMessage()` fires before the scheduler processes the queue, `_uiState` emits with nobody collecting, so Turbine sees the stale initial state. Fix: (1) `buildViewModel()` is a `TestScope` extension that immediately starts a `backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }` to keep `WhileSubscribed` active; (2) single-send tests use `advanceUntilIdle()` + `vm.uiState.value` instead of `awaitItem()`, since `awaitItem()` can race intermediate `Thinking` emissions vs the final state. Multi-send tests that check `slot` captures via Turbine work fine because they don't assert on specific intermediate emissions.
