# Chat Tool Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chat answers wardrobe questions by calling exact, read-only data tools (search by color/category/season, wear history, laundry, stats) instead of letting the model filter a top-5 similarity list. This works for Gemini Nano and the three cloud providers.

**Architecture:**
- A platform-neutral tool layer in `core/data` commonMain:
  - `ToolSpec` declarations and `WardrobeTool` implementations over the existing DAOs and repositories;
  - a `ToolChatRunner` that drives the call loop and then validates the final answer.
- Each provider that can call tools implements `ToolCallingChatProvider`, mapping `ToolSpec` and turns to its own wire format: ML Kit `FunctionDeclaration`, OpenAI `tools`, Anthropic `tool_use`, Gemini `functionDeclarations`.
- `ChatRepository` uses the tool path whenever the active provider supports it at runtime. Otherwise it keeps today's `PatternChatRouter` + RAG path unchanged.

**Tech Stack:**
- Kotlin 2.4, kotlinx.serialization `JsonObject` for tool arguments and results, Room KMP DAOs, Koin.
- ML Kit `genai-prompt` 1.0.0-beta5 (`GenerateContentRequest.Builder.setTools`, `Candidate.functionCalls`, `FunctionResponsePart`, `Feature.FUNCTION_CALLING = 5`).
- Ktor, for the existing cloud providers.

**Spec:** GitHub issue #75, plus the design rulings below, made with the owner on 2026-11-15 after on-device Nano testing showed the model can't reliably filter a top-5 list by color. There's no separate spec doc; this plan's "Design rulings" section is the spec.

## Design rulings
1. **Tools first, router as fallback.**
   - When the selected provider reports tool support (Nano: `getAvailableFeatures()` contains `FUNCTION_CALLING`; cloud: always, until the server rejects tools), chat goes through `ToolChatRunner`. Neither the router nor the RAG context injection runs.
   - Otherwise, today's path runs unchanged: foss without a cloud key, Nano without function calling, or an OpenAI-compatible server that returned a tools-unsupported error.
2. **Read-only tools only.** No writes. `ChatAction` chips stay as they are: the user taps to act.
3. **The final answer keeps today's JSON contract** (`ChatResponseParser`). The runner then **drops every `item_id` that didn't appear in a tool result during this turn**. If an `items` or `outfit` reply is left with no ids, it becomes `Text`. This is the guard against made-up matches.
4. **At most 4 tool rounds per user message.** After that, the provider is called with tools disabled and must answer.
5. **Tool results** are compact JSON, and item lists are capped at 20. An unknown filter value returns `{"error": "...", "valid_values": [...]}` instead of guessing.
6. **Tool names** are `snake_case`, and **every description is ≤ 180 characters.** A sibling project found that longer descriptions silently break every tool in some runtimes.
7. **History:** the runner sends the prior `ConversationTurn`s as plain user/assistant turns. Tool calls and results from earlier messages aren't replayed.

## Global Constraints
- **Layering:**
  - `core/data` commonMain stays platform-free; `compileCommonMainKotlinMetadata` catches leaks.
  - ML Kit types stay in `platform/gms`, and foss must stay GMS-free.
  - `CancellationException` is rethrown at every catch and every `Result` unwrap.
  - Repositories return `DataResult` (CLAUDE.md). Tools may call DAOs directly, as the router does today.
- **Test names:** no commas or other characters Kotlin/Native rejects in backticked names in commonTest (`ios-compile` compiles them).
- **UI:** never show raw exception text; `chatErrorMessage` stays the only mapper.
- **Gradle:** run it from `app-android/`, one invocation at a time, in the foreground, no `--rerun-tasks`, never kill daemons. Commits are signed (1Password); never bypass signing.

## Review Focus
1. **Made-up IDs:** a final answer citing an id no tool returned must lose it. (`ToolChatRunnerTest`, Task 2.)
2. **Unknown or variant filter values** ("grey" vs "Grey", "navy blue", "jacket" vs category "Outerwear") resolve case-insensitively against the lookup tables, with subcategory names mapping to their category. Anything else gets an error listing the valid values, never an empty "none" result. (`SearchItemsToolTest`, Task 1.)
3. **A tool that throws** (database error): the runner sends `{"error":"tool failed"}` back to the model and keeps going. It never crashes and never leaks exception text. (Task 2.)
4. **A provider that keeps calling tools:** stops after 4 rounds with a forced answer. (Task 2.)
5. **A cloud server that rejects `tools`** (OpenAI-compatible local servers): the provider reports unsupported for the rest of the session, and that message is retried on the RAG path. (Task 5.)

---

## PR 1: tool layer, runner, wiring (Tasks 1–3)

### Task 1: Tool declarations and the 8 read-only wardrobe tools

**Files:**
- Create (core/data commonMain `ai/tools/`):
  - `ToolSpec.kt`, holding `ToolSpec`, `ToolParam`, `ToolParamType`;
  - `WardrobeTool.kt`, holding the `WardrobeTool` interface and `ToolResult`;
  - `WardrobeTools.kt`, holding the 8 tools;
  - `ToolRegistry.kt`.
- Modify: `di/DataKoinModule` (commonDataModule): `single { ToolRegistry(listOf(...)) }`.
- Tests: `core/data/src/jvmTest/.../ai/tools/WardrobeToolsTest.kt` against the real in-memory Room DB (the existing jvmTest DB helper) with a fake `EmbeddingEncoder`; `commonTest/.../ai/tools/ToolSpecTest.kt`.

**Interfaces (produces):**
- `enum class ToolParamType { STRING, INTEGER, BOOLEAN, STRING_ARRAY }`
- `data class ToolParam(val name: String, val type: ToolParamType, val description: String, val required: Boolean = false, val enumValues: List<String>? = null)`
- `data class ToolSpec(val name: String, val description: String, val params: List<ToolParam>)`
- `data class ToolResult(val json: JsonElement, val itemIds: Set<Long>)`. `itemIds` = every item id appearing in `json`.
- `interface WardrobeTool { val spec: ToolSpec; suspend fun run(args: JsonObject): ToolResult }`
- `class ToolRegistry(val tools: List<WardrobeTool>)`, with `val specs: List<ToolSpec>` and `fun find(name: String): WardrobeTool?`.
- **An item in a result** serializes as `{"id", "name", "category", "subcategory"?, "colors": [...], "wash_status", "wear_count"}`.
- **The tools**, name → params → data source:
  1. `search_items`
     - Params: `query?: STRING`, `colors?: STRING_ARRAY`, `category?: STRING`, `season?: STRING`, `occasion?: STRING`, `include_archived?: BOOLEAN` (default false), `limit?: INTEGER` (default 10, max 20).
     - Source: `ClothingDao.getAllClothingItemDetails().first()`, filtered in memory.
     - Filter matching:
       - A color matches if the item has **any** of the requested colors.
       - Values resolve case-insensitively against the lookup tables. A subcategory name ("Jacket") maps to its category.
       - A color word also matches every seeded color whose name contains it as a whole word: "blue" → Royal Blue + Sky Blue, "green" → Forest Green. Navy is matched only by "navy".
       - An unknown value returns the error with `valid_values`.
     - With `query`: rank the filtered items by `EmbeddingIndex` similarity when `encoder.isAvailable`, else by name/description substring.
     - Result: `{"items": [...], "total_matches": n}`.
  2. `get_items` (`item_ids: STRING_ARRAY` of numeric strings): `getItemDetailsByIds`.
  3. `wear_history` (`item_id: INTEGER`): `{"item": {...}, "wear_count", "last_worn": date?, "recent_dates": [≤5]}`, via `LogRepository.getLogsForItem(id).first()`.
  4. `items_not_worn_since` (`days: INTEGER`, `category?`): `ClothingDao.getItemsNotWornSince`.
  5. `most_worn` (`limit?: INTEGER` default 5, `category?`): `StatsDao.getMostWornItems().first()`.
  6. `outfits_worn_on` (`date: STRING` `YYYY-MM-DD`): `LogDao.getLogsForDateOnce`. A bad date returns an error.
  7. `laundry_list` (no params): `ClothingDao.getItemsNeedingWash`.
  8. `wardrobe_summary` (no params): `{"total_items", "by_category": {...}, "by_color": {...}}`, via `getItemCount` + `StatsDao` breakdowns `.first()`.

- [ ] **Step 1: Write failing tests**
  - `ToolSpecTest`: every spec in a registry built from all 8 tools has a `snake_case` name, a description ≤ 180 characters, and unique names.
  - `WardrobeToolsTest`, seeded with: Yellow sneakers (Footwear/Sneakers, Yellow); Grey overcoat (Outerwear/Coat, Grey); Blue T-shirt (Tops/T-Shirt, Royal Blue + Sky Blue).
    - `search_items(colors=["yellow"])` returns exactly the sneakers' id, and `itemIds == {sneakersId}`.
    - `search_items(colors=["purple"])` returns `items = []` with `total_matches = 0` and no `error`.
    - `search_items(colors=["chartreuse"])` returns an `error` containing `valid_values` that includes `"Yellow"`.
    - `search_items(category="jacket")` is treated as Outerwear and returns the overcoat.
    - `search_items(colors=["blue"])` returns exactly the T-shirt (Royal Blue and Sky Blue match by whole word). `search_items(colors=["navy"])` returns no items and no error.
    - `wear_history` after 2 logged wears: `wear_count = 2`, with `last_worn` set.
    - `outfits_worn_on("2026-13-40")` returns an `error`.
    - `laundry_list` returns only dirty items.
    - `wardrobe_summary.total_items = 3`.
- [ ] **Step 2: Run them; they fail to compile.** `./gradlew :core:data:jvmTest`
- [ ] **Step 3: Implement the types, the 8 tools, the registry and the Koin binding.**
- [ ] **Step 4:** `./gradlew :core:data:jvmTest :core:data:compileCommonMainKotlinMetadata` → PASS.
- [ ] **Step 5: Commit** `feat(chat): read-only wardrobe tools and registry (#75)`

### Task 2: `ToolChatRunner`: call loop and answer validation

**Files:**
- Create (core/data commonMain `ai/tools/`):
  - `ToolTurn.kt`: `ToolCall`, `ToolCallResult`, `ToolTurn`, `ToolStep`;
  - `ToolCallingChatProvider.kt`;
  - `ToolChatRunner.kt`.
- Modify: `ai/ChatPromptPrefix.kt`, adding `TOOL_SYSTEM_PROMPT`.
- Tests: `commonTest/.../ai/tools/ToolChatRunnerTest.kt`, with a scripted fake provider and fake tools.

**Interfaces (produces):**
- `data class ToolCall(val id: String, val name: String, val args: JsonObject)`
- `data class ToolCallResult(val callId: String, val name: String, val result: JsonElement)`
- `sealed interface ToolTurn`, with variants:
  - `data class User(val text: String)`;
  - `data class Assistant(val text: String?, val calls: List<ToolCall> = emptyList())`;
  - `data class ToolResults(val results: List<ToolCallResult>)`.
- `sealed interface ToolStep`, with variants:
  - `data class Calls(val calls: List<ToolCall>)`;
  - `data class Final(val text: String)`, holding the raw JSON reply text.
- `interface ToolCallingChatProvider`:
  - `suspend fun supportsTools(): Boolean`;
  - `suspend fun step(system: String, turns: List<ToolTurn>, tools: List<ToolSpec>, allowTools: Boolean): Result<ToolStep>`.
- `class ToolChatRunner(registry: ToolRegistry)`, with `suspend fun run(provider: ToolCallingChatProvider, userMessage: String, history: List<ConversationTurn>): Result<ChatResponse>`.
  1. Build the turns: history, then `User(userMessage)`.
  2. Loop for up to 4 rounds:
     - `Calls` → run each call. An unknown tool or a thrown exception gives `{"error":"tool failed"}`, and `CancellationException` is rethrown. Collect `seenIds`, append `Assistant(null, calls)` and `ToolResults`, and continue.
     - `Final` → stop.
  3. After round 4, one more `step` call with `allowTools = false`.
  4. Parse `Final.text` with `ChatResponseParser`, then **validate**: keep only `itemIds` in `seenIds` (also the action's ids). Downgrade empty `WithItems`/`WithOutfit` to `Text(text)`. Drop an action whose ids are no longer valid.
- `TOOL_SYSTEM_PROMPT`:
  - Use the tools for every factual question about the user's wardrobe, wear history or laundry, and never answer those from memory.
  - The final answer must use the existing JSON format.
  - `item_ids` may only contain ids returned by a tool in this conversation turn.
  - Refer to items by name, never by id.
  - If tools return nothing, say so plainly.
  - Reuse the JSON-format block from `SYSTEM_PROMPT`; don't duplicate it as a copy, extract a shared constant.

- [ ] **Step 1: Write failing tests** (`ToolChatRunnerTest`)
  - The provider calls `search_items` and gets `{id: 2}` back, then answers Final `{"type":"items","text":"…","item_ids":[2,1]}` → `WithItems` with `itemIds == [2]`.
  - A Final `items` reply with ids that no tool returned → `Text`.
  - The provider returns `Calls` 5 times → `step` is called 5 times in total, and the 5th has `allowTools = false`.
  - A tool throws `IllegalStateException("db secret")` → the next step's `ToolResults` contain `{"error":"tool failed"}` and no `"db secret"`.
  - A tool throws `CancellationException` → it propagates.
  - An unknown tool name → `{"error":"tool failed"}`, and the loop continues.
- [ ] **Step 2: Run** `./gradlew :core:data:jvmTest`; it fails to compile.
- [ ] **Step 3: Implement** the types, runner and prompt.
- [ ] **Step 4:** `./gradlew :core:data:jvmTest :core:data:compileCommonMainKotlinMetadata` → PASS.
- [ ] **Step 5: Commit** `feat(chat): tool-calling runner with answer validation (#75)`

### Task 3: Wire `ChatRepository` and the selector; docs

**Files:**
- Modify:
  - `features/chat/.../ChatRepository.kt`: if the provider from `ChatAiProviderSelector.current()` is a `ToolCallingChatProvider` and `supportsTools()`, return `ToolChatRunner.run(...)`; else today's router + RAG path.
  - `di/ChatKoinModule.kt`: inject `ToolChatRunner`.
  - `app-android/CLAUDE.md` ("RAG pipeline" becomes "Chat pipeline").
- Tests: `features/chat/src/test/.../ChatRepositoryToolPathTest.kt`, with fakes.

- [ ] **Step 1: Failing tests**
  - A tool-capable provider with `supportsTools() = true` → the runner is used, and neither the router nor the encoder is called.
  - `supportsTools() = false` → today's path, with the router consulted first.
  - A plain `ChatAiProvider` → today's path.
- [ ] **Step 2–4:** Implement. Then `./gradlew test lint assembleFullDebug assembleFossDebug` → PASS.
- [ ] **Step 5: Commit** `feat(chat): route chat through tools when the provider supports them (#75)`
- [ ] **PR 1 checkpoint:**
  - No provider implements tools yet, so behavior is unchanged. Confirm that a chat smoke test on the test phone still works on the RAG path.
  - Push, open the PR, wait for CI to pass, and wait for the owner to merge.

---

## PR 2: providers (Tasks 4–6)

### Task 4: Nano tool calling (`platform/gms`)

**Files:**
- Create: `platform/gms/.../chat/NanoToolMapping.kt`, with these pure functions:
  - `ToolSpec.toFunctionDeclaration()`, using `FunctionSchema(FunctionType.OBJECT, …properties, required)`, with `STRING_ARRAY` as an `ARRAY` of `STRING` and `enumValues` passed as `enum`;
  - `FunctionCall.toToolCall()`, mapping `FunctionArgValue` subtypes to a `JsonObject`;
  - `List<ToolTurn>.toContents()`, where `Assistant` calls become `MODEL` contents with `FunctionCallPart` and `ToolResults` become `FunctionResponsePart(id, name, map)`.
- Modify: `NanoChatProvider` implements `ToolCallingChatProvider`.
  - `supportsTools()` is `model.getAvailableFeatures().contains(Feature.FUNCTION_CALLING)`, cached.
  - `step()` builds `GenerateContentRequest.Builder(contents)` with `systemInstruction` and `setTools(...)` when `allowTools` is true.
  - A candidate with non-empty `functionCalls` → `Calls`; else → `Final(text)`.
  - On `Final` with `allowTools = false`, use the structured `NanoChatReply` path when available.
- Tests: `NanoToolMappingTest`:
  - spec → declaration types, required list and enum;
  - each `FunctionArgValue` subtype → the JSON value;
  - turns → contents roles and part types.

  If `FunctionArgValue` subtypes can't be constructed in a host test, cover them through `FunctionCall` built with real values. Read `com.google.mlkit.genai.common.FunctionArgValue$*` with javap before writing the test.
- [ ] TDD steps as above. Then `./gradlew :platform:gms:testDebugUnitTest test lint assembleFullDebug assembleFossDebug`.
- [ ] **Commit** `feat(chat): Gemini Nano tool calling (#75)`

### Task 5: OpenAI-compatible tool calling

**Files:** modify `OpenAiChatProvider`, and create `features/chat/.../ai/OpenAiToolFormat.kt`, with pure request-JSON and response-parsing functions. Tests: `OpenAiToolFormatTest`.
- **Request:** `tools: [{type:"function", function:{name, description, parameters:<JSON schema>}}]`, with `tool_choice: "auto"`, or the field left out when `allowTools = false`.
- **Turns:** `ToolTurn.Assistant` calls become an assistant message with `tool_calls`; `ToolResults` become `role:"tool"` messages, each with its `tool_call_id`.
- **Response:**
  - `choices[0].message.tool_calls` → `Calls`. Arguments are a JSON string; parse it, and treat a parse failure as `{"error"}` for that call.
  - Otherwise `content` → `Final`.
- **Unsupported servers:** an HTTP 400/404/422 whose body mentions `tools` or `tool_choice` makes `supportsTools()` return false for this provider instance. `step()` returns failure with a typed `ToolsUnsupportedException`, and `ChatRepository` retries that message on the RAG path.
- Tests: request JSON shape, a response with `tool_calls`, a response with content, malformed `arguments`, and an unsupported error marking the provider.
- [ ] **Commit** `feat(chat): OpenAI-compatible tool calling (#75)`

### Task 6: Anthropic and Gemini tool calling

**Files:** `AnthropicToolFormat.kt` and `GeminiToolFormat.kt` (pure), plus the provider changes. Tests: one format test each.
- **Anthropic:**
  - The request carries `tools: [{name, description, input_schema}]`. Assistant `content` blocks become `{type:"tool_use", id, name, input}`, and results become a user turn of `{type:"tool_result", tool_use_id, content}`.
  - A response containing `stop_reason:"tool_use"` → `Calls`.
- **Gemini:**
  - The request carries `tools: [{functionDeclarations: [...]}]`, plus `toolConfig` mode `NONE` when `allowTools = false`.
  - Turns are `model` parts `{functionCall:{name,args}}` and `user` parts `{functionResponse:{name,response}}`.
  - A response with `functionCall` parts → `Calls`. The ids are synthesized as `name#index`.
  - Keep `responseMimeType: application/json` only on the final, tools-off step.
- [ ] **Commit** `feat(chat): Anthropic and Gemini tool calling (#75)`
- [ ] **PR 2 checkpoint:**
  - Push, open the PR, wait for CI to pass.
  - **Owner device test on the Nano phone**, on the 3-item closet, the same questions as before: "any yellow items?", "anything purple?", "anything green?", "blue?", "what did I wear last?", "what haven't I worn in a month?". The expected answers are exact, with no made-up cards.
  - A cloud provider is tested only if the owner has a key configured.
  - Then wait for the owner to merge.
