# Recommendation scoreboard, PR 2 (personas and baseline): implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** five curated personas run through the PR 1 scoreboard. Their results are committed as a baseline that later engine changes can't fall below without the owner deciding so, and a colour-harmony sensitivity check proves the scoreboard can see colour.

**Architecture:**
- **Task 1** adds a `measure-colors` command to `tools/demo-backup`. It measures photos with the app's `ColorExtractor`.
- **Task 2** is curation, done by the controller in session from the full Kaggle dataset. It produces five committed Kotlin fixtures plus a local, uncommitted photo sheet for the owner's veto.
- **Task 3** adds a test-only harmony switch on the engine, the sensitivity test, and the baseline assertion.
- **Task 4** records the baseline.

**Tech stack:** Kotlin/JVM (`tools/demo-backup`, `features/recommendations` jvmTest), Python 3 for the controller's local curation scripts (scratchpad only, never committed), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-11-27-scoreboard-design.md` (approved), "Delivery 2". Issue: #148, which also closes #133. Builds on PR #150.

## Global Constraints
- **No dataset content in the repo.**
  - Committed fixtures hold only our own generic descriptions, seeded lookup names, measured hexes and shares, and scoreboard tags.
  - No Kaggle product names, ids, brands or images.
  - The key → dataset id mapping and the photo sheet live outside the repo, under `~/Datasets/fashion-product-images-full/` (the owner's machine).
- **Gap items** (owner decision 2026-11-27): cold and rain gear the dataset lacks (boots, coats, rain jackets, blazers and similar) are hand-described items. They have no photo and hand-set hexes, and are marked `photo = false` on the sheet. Every other item comes from the dataset, with its colours measured by Task 1.
- **Disk:** read the dataset straight from `C:\Users\blain\Downloads\archive.zip`, skipping the duplicate nested `fashion-dataset/fashion-dataset/` folder. Extract only the chosen items' images and detail JSON, never the whole archive.
- **Coherent wardrobes:**
  - each persona draws from the sections it would really shop in (menswear for the men's personas, womenswear for the women's);
  - items are filed the way a user would file them (Activewear stays Activewear);
  - heels are filed as Dress Shoes.
- **The five personas** (spec §3): office commuter (menswear, four seasons, cold winter); office commuter (womenswear, dresses and heels, four seasons); creature of habit (menswear, small, mild and rainy); mix-and-matcher (womenswear, large, warm with hot summers); active student (mostly athletic and casual, cool, wet and windy).
- **Engine change:** only an internal, test-only harmony switch. It must not change production behaviour or the Koin graph. `AppKoinGraphTest` and the `recommendationsModule` `verify()` must pass unchanged.
- **The baseline:**
  - committed as constants with the date and the engine commit in a comment;
  - the assertion fails when a metric is worse than its baseline by more than the tolerance;
  - the tolerance is 0, since runs are deterministic. A change meant to move a metric re-baselines in its own PR, with before and after numbers.
- **Inherited from PR 1:** no circular checks, determinism, signed commits with the Opus 5.5 and Claude-Session trailers, one Gradle invocation at a time in the foreground, and never stage `.claude/settings.json`.

## Review Focus
1. **A dataset photo the extractor can't read, or one it returns no colours for.** `measure-colors` reports it per id, and the curation step falls back to a hand-set hex marked as such. It must never silently write an empty colour list. Test: `unreadable or colourless images are reported, not skipped` (Task 1).
2. **A fixture persona fails `Persona.validate()`,** for example a duplicate key or an unknown lookup name. The scoreboard fails loudly. Covered by the PR 1 validation, plus a test that each of the five personas validates (Task 3).
3. **The harmony switch leaks into production.** The default must equal today's engine, with byte-identical scores. Test: `default engine scores equal the harmony on engine` (Task 3).
4. **The baseline assertion is vacuous,** for example comparing a metric with itself or using a missing persona key. Test: `a worse metric fails the baseline check`, using a synthetic `PersonaResult` (Task 3).
5. **Runtime.** Five personas, each a year long, must stay under 3 minutes. If they don't, sample every 3rd logged day (already the rule), or shorten the warm-up. Report the measured time (Task 4).

---

### Task 1: `measure-colors` in `tools/demo-backup`

**Files:**
- Create: `tools/demo-backup/src/main/kotlin/com/closet/tools/demobackup/MeasureColors.kt`
- Modify: `tools/demo-backup/src/main/kotlin/com/closet/tools/demobackup/Main.kt` (the subcommand and its usage), `tools/demo-backup/README.md` (one usage line)
- Test: `tools/demo-backup/src/test/kotlin/com/closet/tools/demobackup/MeasureColorsTest.kt`

**Interfaces:**
- **Produces:** `measure-colors <imagesDir> <idsFile> <outJson>`.
  - `idsFile` has one image id per line.
  - The output is JSON: `{"<id>": {"colors": [{"hex": "#RRGGBB", "share": 0.42, "colorName": "Navy"}], "status": "ok" | "unreadable" | "no-colours"}}`, with ids in input order.
  - It uses `ColorExtractor` with the same seeded reference shades and defaults that `ColorCheck` uses, so it names colours exactly the way `ColorCheck` and the app do.
  - `object MeasureColors { fun measure(imagesDir: File, ids: List<String>): Map<String, Measured> }`, where `data class Measured(val status: String, val colors: List<ExtractedColor>)`.
- **No dataset content** goes in the repo. Tests generate their own images.

- [ ] **Step 1: Write the failing tests:**
  - `a two colour image measures both colours with their shares`: a generated 40×40 PNG, half navy and half white, on a transparent or border background as `ColorCheck` expects. Assert two colours, with shares within 0.05 of the expected values;
  - `unreadable or colourless images are reported, not skipped` (Review Focus 1);
  - `output keeps input order`.
- [ ] **Step 2: Run** `./gradlew :tools:demo-backup:test --console=plain -q`. **Expected:** compile failure, then behavioural failures against stubs. Record both.
- [ ] **Step 3: Implement.** Reuse `ColorCheck`'s shade list and image loading rather than duplicating them. Extract a shared helper if needed.
- [ ] **Step 4: Run** the same command. **Expected:** PASS.
- [ ] **Step 5: Commit** `feat(demo-backup): measure-colors command for scoreboard curation (#148)`.

### Task 2: Curate the five personas (controller, in session)

This task is judgement work done by the controller, not a subagent. Its scripts stay in the scratchpad.

- [ ] **Step 1: Pools.** From `styles.csv` (already extracted to `~/Datasets/fashion-product-images-full/`), build each persona's candidate pool, filtered by gender, articleType and usage. Read the detail JSON (fabric, pattern, sleeve) from the zip for the shortlisted ids only.
- [ ] **Step 2: Wardrobes and catalogues.** For each persona:
  - choose a wardrobe and 20–40 outfits by taste;
  - add hand-described gap items where the climate needs them;
  - tag everything: seasons, occasions, materials, warmth 0–3, rainReady, formality 1–5, favourite weights and which outfits are saved;
  - **saved share:** the creature of habit saves nearly all its outfits, the mix-and-matcher almost none, and the others their top 5–10.

  Write the local mapping (`mapping.json`, key → dataset id or "hand").
- [ ] **Step 3: Images.** Extract only the chosen ids' images from the zip into `~/Datasets/fashion-product-images-full/images/`.
- [ ] **Step 4: Colours.** Run `measure-colors` on those images.
  - **The extraction path:** the border background path, which is the app's FOSS path. The full flavour's ML Kit segmentation runs only on Android, and it keeps the whole subject anyway, model included.
  - **Prefer product-only photos** over model shots when choosing items.
  - **Cross-check each measured colour against the dataset's `baseColour`:**
    - drop a skin-tone colour the label doesn't explain, for example a beige tag on a "Navy Blue" shirt;
    - give ids that are unreadable or have no colours a hand-set hex;
    - record every correction in the mapping as `hand-corrected`, so the photo sheet shows it.
- [ ] **Step 5: Fixtures.** Generate `features/recommendations/src/jvmTest/kotlin/com/closet/features/recommendations/scoreboard/personas/<Name>Persona.kt` for each persona.
  - **Contents:** our descriptions, seeded names, measured hexes and the tags. No dataset text.
  - **Verify:** `Persona.validate()` passes, and a grep for brand or product names across the generated files finds nothing.
- [ ] **Step 6: Photo sheet.** Build one local HTML page per persona (outside the repo) showing each outfit's photos, with the hand-described items as text cards. Ask the owner to veto anything implausible, and replace what's vetoed.
- [ ] **Step 7: Commit** the five fixture files: `test(recommendations): five curated scoreboard personas (#148)`.

### Task 3: Harmony switch, sensitivity test and baseline assertion

**Files:**
- Modify: `features/recommendations/src/commonMain/kotlin/com/closet/features/recommendations/engine/OutfitRecommendationEngine.kt`. Add an internal harmony switch; the default is on.
- Modify: `scoreboard/Scoreboard.kt`, so `run` takes an engine (default: production).
- Create:
  - `scoreboard/Baseline.kt`: per-persona baseline constants and the comparison;
  - `scoreboard/BaselineTest.kt`;
  - `scoreboard/SensitivityTest.kt`;
  - `scoreboard/AllPersonas.kt`: the list of the five personas.
- Test: `features/recommendations/src/commonTest/.../engine/HarmonySwitchTest.kt`, or jvmTest if commonTest can't reach it.

**Interfaces:**
- **Consumes:** PR 1's `Scoreboard.run(persona, start, days)`, `PersonaResult` and `CheckRate`, and Task 2's personas.
- **Produces:**
  - **Engine:** `OutfitRecommendationEngine` gains an internal way to turn colour harmony off, so the harmony multiplier is ×1.0. Production construction stays exactly as it is today:
    - Koin's `singleOf(::OutfitRecommendationEngine)` or the equivalent keeps resolving;
    - existing `OutfitRecommendationEngine()` calls compile unchanged;
    - **suggested shape:** a public no-arg constructor delegating to an internal one with `colorHarmony: Boolean = true`. Confirm that it doesn't make the constructor reference ambiguous for Koin. If it does, use an internal factory or an internal `copy`-style function instead.
  - `Scoreboard.run(persona, start, days, engine: OutfitRecommendationEngine = OutfitRecommendationEngine())`.
  - `object Baseline { val byPersona: Map<String, PersonaResult>; fun regressions(actual: PersonaResult, baseline: PersonaResult): List<String> }`.
    - **The rule:** every check's fail rate may not exceed its baseline. Hit@10 may not fall below its baseline. The no-results rate may not exceed its baseline.
    - Rates are compared as fractions with tolerance 0, but a rate on 0 applicable is treated as unchanged.
    - Each regression line names the persona, the metric, the baseline and the actual value.
  - `AllPersonas.list: List<Persona>`.

- [ ] **Step 1: Write the failing tests:**
  - `default engine scores equal the harmony on engine` (Review Focus 3): the same input gives identical `scoreCombo` values;
  - `harmony off scores ignore colour`: two combos that differ only in colour harmony score equally when it's off, and differently when it's on;
  - `a worse metric fails the baseline check` and `an equal or better result passes the baseline check` (Review Focus 4), with synthetic `PersonaResult`s;
  - `every curated persona is valid` (Review Focus 2);
  - in `SensitivityTest`, `turning harmony off moves the scoreboard`: across the five personas, at least one of hit@10 or a check's fail rate differs between harmony on and off. If none does, the test fails with a message saying the scoreboard can't see colour. Don't weaken it; report it.
  - in `BaselineTest`, `no persona regresses against the baseline`, which asserts `Baseline.regressions` is empty for each persona. Until Task 4 records the numbers, `Baseline.byPersona` stays empty, and this test fails with "no baseline recorded for <persona>". That is the RED for Task 4.
- [ ] **Step 2: Run** `./gradlew :features:recommendations:scoreboard --console=plain -q` and `./gradlew :features:recommendations:jvmTest --console=plain -q`. **Expected:** the new tests fail.
- [ ] **Step 3: Implement**, except recording the baseline numbers.
- [ ] **Step 4: Run** the scoreboard and jvmTest again, then `./gradlew :app:testFullDebugUnitTest --tests "*AppKoinGraphTest*" --console=plain -q` and `./gradlew :features:recommendations:compileTestKotlinIosSimulatorArm64 --console=plain -q`. **Expected:** PASS everywhere except `no persona regresses against the baseline`, which is the expected RED for Task 4.
- [ ] **Step 5: Commit** `test(recommendations): harmony sensitivity check and baseline assertion (#148)`.

### Task 4: Record the baseline (controller)

- [ ] **Step 1:** run the scoreboard and read `build/reports/scoreboard/scoreboard.md`.
- [ ] **Step 2:** write the five personas' numbers into `Baseline.byPersona`, with a comment giving the date and the engine commit (`git rev-parse --short HEAD`).
- [ ] **Step 3:** re-run. **Expected:** every test passes. Report the wall time against the 3-minute target.
- [ ] **Step 4: Commit** `test(recommendations): record the scoreboard baseline (#148)`, with the report table in the body.

## Whole-branch verification (controller)
- **The full pre-PR command** from `app-android/CLAUDE.md`.
- **A grep of the diff** for dataset brand or product names, which must find nothing.
- **The PR body:** "Closes #148, closes #133", with the baseline table and the sensitivity result.
