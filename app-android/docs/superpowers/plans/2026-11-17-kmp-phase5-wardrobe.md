# KMP Phase 5f: `features/wardrobe` → KMP/CMP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `features/wardrobe`, the last Phase 5 feature, into a Kotlin Multiplatform + Compose Multiplatform module, with Android behaviour unchanged. That covers the closet grid and filters, item detail, the add/edit form, bulk wash and brand management: their five ViewModels, routes, UI and the common Koin module all go to `commonMain`. After this PR, no module uses `material-icons-extended`.

**Architecture:**
- **One PR, four tasks.** This is the largest module (≈6.5k lines) and the only one with a real photo pipeline: picking, background removal, color extraction and captioning.
- **Task 1 (`core/ui`):** shared display helpers wardrobe needs:
  - `formatDateLong` (localized "April 4, 2026");
  - `formatCurrency` (the device locale's currency);
  - `parseHexColor`, moved up from `features/stats` so both modules share it.
- **Task 2 (module + resources):** convert the build to KMP with *everything* still in `androidMain`, then move strings and plurals to Compose resources. Doing resources first lets Task 3 move ViewModels that carry error-message resources.
  - `ClothingFormViewModel.errorMessage: Int?` becomes `StringResource?`.
- **Task 3 (seams + ViewModels):** add the platform seams, then move the ViewModels, form state, routes and a common Koin module to `commonMain`.
  - **`ItemPhotoProcessor`** (wardrobe commonMain interface; `AndroidItemPhotoProcessor` in androidMain) owns every `Uri`/`Bitmap`/Palette/`StorageRepository`/segmenter/captioner call the form ViewModel makes today. The code moves verbatim.
  - **`CategoryShortcutPinner`** owns the pinned category shortcut that `ClosetViewModel` builds today.
  - `ShortcutUsageReporter` (it already exists) replaces `ShortcutManagerCompat.reportShortcutUsed`.
  - `ImagePathResolver` replaces `File`.
  - `java.time` → kotlinx-datetime, Timber → Kermit.
  - `toRoute` sits behind small `expect` readers, the same as outfits.
  - `BuildConfig.DEBUG` → the existing `BuildFlags`.
- **Task 4 (UI):** every `Icons.*` → Material Symbols, and the screens move to `commonMain`. Two Android-only UI hooks sit behind `expect` composables:
  - the photo picker;
  - `BackHandler`.

  The debug card's clipboard entry is a small `expect` too.
- **Stays in `androidMain`:**
  - `WardrobeNavigation.kt`'s `NavGraphBuilder` extensions (Phase 6), with the `@Serializable` destinations moving to commonMain;
  - previews;
  - `BatchSegmentationWorker` and the WorkManager-backed `BatchSegmentationScheduler`;
  - `SubjectSegmenter` and the `Unsupported*` stubs (they are `Bitmap`-typed and bound per flavor);
  - `wardrobeNoOpModule`;
  - `AndroidItemPhotoProcessor` and `AndroidCategoryShortcutPinner`;
  - the `toRoute`/picker/back/clipboard actuals;
  - the launcher-shortcut drawables and `colors.xml`.
- **Koin:**
  - commonMain gets `commonWardrobeModule` (the five ViewModels).
  - androidMain keeps the public name `wardrobeModule`, which `includes(commonWardrobeModule)` and adds the scheduler, the worker, `AndroidItemPhotoProcessor` and `AndroidCategoryShortcutPinner`. So `app` and `platform/gms` imports don't change.
  - Flavor binding is unchanged: `gmsModule` (full) or `wardrobeNoOpModule` (foss).

**Tech Stack:** Kotlin 2.4.21, AGP 9.1.0 KMP library, CMP 1.12.2 / Material 3 1.9.0, Compose resources (including plurals), kotlinx-datetime 0.8.0, Koin 4.2.2, JetBrains lifecycle 2.10.0, Coil 3.6.3, Kermit 2.2.0.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md` (Phase 5).
- **Models:**
  - `plans/2026-11-16-kmp-phase5-outfits.md`: the shared core helpers task, the `expect` route readers (`OutfitBuilderArgs.*.kt`), `ShortcutUsageReporter`;
  - `plans/2026-11-17-kmp-phase5-chat.md`: build shape, JVM Koin graph test, strings test, Material Symbols.
- **Gotchas:** CLAUDE.md "Resources" and `docs/superpowers/notes/kmp-toolchain.md`.

## Global Constraints

- **Exit criteria:**
  - `assembleFullDebug` and `assembleFossDebug` succeed.
  - All unit tests pass, including every module's `jvmTest`.
  - The foss APK has no GMS classes.
  - A manual smoke test passes on the owner's **test** device.
  - CI `ios-compile` is green.
- **Targets:** `android`, `jvm()`, `iosArm64()`, `iosSimulatorArm64()`.
- **Versions:**
  - No new libraries; that includes no `ui-backhandler` and no image libraries. Any new iOS klib must have ABI ≤ **2.4.0**.
  - Declare CMP libraries by catalog coordinates, never `compose.*` accessors.
- **`commonMain` stays platform-free:** no `android.*`, `java.*`, `androidx.core`, `androidx.navigation`, `androidx.activity`, `androidx.work`, `androidx.palette` or `timber` imports. `:features:wardrobe:compileCommonMainKotlinMetadata` catches leaks.
- **Resources:**
  - Positional placeholders only (`%1$s`, `%1$d`).
  - `'`, not `\'`; `"`, not `\"`.
  - Literal characters, not `\uXXXX`.
  - Plurals stay plurals (`pluralStringResource` / `getPluralString`).
  - `material-icons-extended` is gone from `features/wardrobe` *and* from `gradle/libs.versions.toml` if nothing else uses it.
- **Icons:** Material Symbols Outlined, wght 400, FILL 0, GRAD 0, opsz 24, `#FF000000`, named `ic_symbol_<name>`.
  - Use Google's real path data, shifted from the `0 -960 960 960` viewBox into a `0 0 960 960` viewport: every absolute y gets +960, relative commands are unchanged. Copy the structure of `ic_symbol_auto_awesome.xml`.
  - Reuse the existing `ic_symbol_*` where one matches. Filled glyphs keep their `_filled` variant: `Favorite` → `ic_symbol_favorite_filled`, `Star` → `ic_symbol_star_filled`.
  - `AutoMirrored` icons get `android:autoMirrored="true"`.
- **Flavors:**
  - Never bind `SubjectSegmenter`, `CaptionEnrichmentProvider` or `BatchCaptioner` in `wardrobeModule` / `commonWardrobeModule`.
  - `wardrobeNoOpModule` keeps its bindings and stays in androidMain.
- **Logging:** Timber → Kermit (`Logger.withTag("<ClassName>")`), with the same levels. Timber `%s`/`%d` arguments become string templates.
- **No users:** no compat shims.
- **Gradle hygiene:**
  - Run from `app-android/`, one invocation at a time, in the foreground, with no `--rerun-tasks`.
  - Never stop or kill daemons.
  - If a task sits with no output for ~5 min, or fails with "Failed to clean up output files", stop and report.
  - iOS compiles locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`. Compile main **and** test (`compileTestKotlinIosSimulatorArm64`).
  - Kotlin/Native rejects `()`, `.`, `:`, `;`, `/`, `<>`, `[]` and `\` in backtick test names.

## Review Focus

1. **Photo pipeline equivalence.** `AndroidItemPhotoProcessor` must preserve today's behaviour; it is the most failure-prone part of this PR.
   - **Pick → import → extract colors → caption.** Captioning happens only when it is supported. The decode runs on IO and `describe` runs on **Main**.
   - **Remove background:**
     - ensure the model;
     - decode at a maximum of 1024 px;
     - segment, then save as a new PNG;
     - re-extract colors from the PNG (Palette skips transparent pixels);
     - re-caption.
   - **Failure** restores the original path and caption.
   - **Revert** deletes the segmented PNG and cancels the caption job.
   - **Stale-path guards:** a late caption never overwrites a newer image.
   - **Discard and replace** delete orphaned files under `NonCancellable`.
   - **Tests:** `ClothingFormViewModelTest` (Task 3) drives these through a fake `ItemPhotoProcessor`:
     - select sets the path, colors and caption;
     - segmentation success swaps the path and re-extracts the colors;
     - segmentation failure restores the original;
     - revert deletes the segmented path;
     - replacing an image deletes the previous one;
     - a stale caption is ignored.
2. **Purchase date round trip.** The Material 3 date picker's UTC epoch millis ↔ `kotlinx.datetime.LocalDate` mapping must match today's `Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()` and its inverse, with no off-by-one in negative-offset time zones. Storage stays ISO `YYYY-MM-DD`, and edit mode parses it back. (Task 4: `internal fun utcMillisToLocalDate(ms: Long): LocalDate` and `localDateToUtcMillis(date: LocalDate): Long`, with common tests for `2026-04-04` both ways and for `0L` → `1970-01-01`.)
3. **Detail-screen money and dates.**
   - Price and cost-per-wear use the device locale's currency format, as `NumberFormat.getCurrencyInstance(Locale.getDefault())` did.
   - Purchase month uses `formatMonthYear`.
   - Wear-log dates were `"MMMM d, yyyy"` with the default locale; they become `formatDateLong`.

   (Task 1: jvmTest for `formatCurrency(12.5)` under `Locale.US` → `"$12.50"`, and `formatDateLong(2026-04-04)` under `Locale.US` → `"April 4, 2026"`. Restore the default locale after each test.)
4. **Launcher shortcuts.**
   - `quick_add` (after a successful add) and `laundry_day` (after a bulk-wash apply) are reported through `ShortcutUsageReporter`.
   - The pinned category shortcut keeps its ID `category_<id>`, its label, its `ic_shortcut_category` icon, its intent (action, extras and `com.closet.MainActivity` class name), and the "pin supported" check.

   (Task 3: ViewModel tests verify the reporter IDs and that `CategoryShortcutPinner.pin(id, label)` is called. The reviewer diff-checks `AndroidCategoryShortcutPinner` against the old `ClosetViewModel` code. Device smoke covers the rest.)
5. **Koin graphs and flavors.**
   - `AppKoinGraphTest` passes in both flavors, and so does `GmsKoinTest`.
   - Foss still resolves the `Unsupported*` stubs; full still resolves the gms segmenter and captioner.
   - The new `WardrobeJvmKoinGraphTest` verifies `commonWardrobeModule` with test-local fakes for `ItemPhotoProcessor` and `CategoryShortcutPinner`, using the same base modules and `extraTypes` as `ChatJvmKoinGraphTest`.
   - The old `WardrobeKoinTest` and `WardrobeStubsTest` move to `androidHostTest`.

---

### Task 1: `core/ui` display helpers: `formatDateLong`, `formatCurrency`, shared `parseHexColor`

**Files:**
- **Modify `core/ui/src/commonMain/.../format/DateLabels.kt`:** add `expect fun formatDateLong(date: LocalDate): String`, the localized long date with year (Android/JVM `DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)` with the default locale; iOS `NSDateFormatter` with `dateStyle = NSDateFormatterLongStyle` on the Gregorian calendar, the same as the other iOS labels).
- **Create `core/ui/src/commonMain/.../format/Currency.kt`:** `expect fun formatCurrency(amount: Double): String` (Android/JVM `NumberFormat.getCurrencyInstance(Locale.getDefault())`; iOS `NSNumberFormatter` with `NSNumberFormatterCurrencyStyle` and the current locale), with actuals beside the date labels.
- **Move** `parseHexColor` from `features/stats/.../StatsColors.kt` to `core/ui/src/commonMain/.../format/HexColor.kt`. Make it public and keep the KDoc and behaviour. Stats imports it from there. Its test moves with it to `core/ui` commonTest.
- **Tests:** `core/ui/src/jvmTest/.../format/CurrencyAndLongDateTest.kt` (Review Focus 3) and the moved hex test.

**Interfaces:**
- **Produces** `com.closet.core.ui.format.formatDateLong(LocalDate): String`, `formatCurrency(Double): String` and `parseHexColor(String?): Color?`.

- [ ] **Step 1:** Write the failing tests.
- [ ] **Step 2:** Run `./gradlew :core:ui:jvmTest` and expect a compile failure.
- [ ] **Step 3:** Implement the expects and actuals and the move.
- [ ] **Step 4:** Verify:
  - `./gradlew :core:ui:jvmTest :core:ui:compileCommonMainKotlinMetadata :features:stats:jvmTest`;
  - `./gradlew :core:ui:compileKotlinIosSimulatorArm64 :core:ui:compileTestKotlinIosSimulatorArm64 -Pkotlin.native.enableKlibsCrossCompilation=true`;

  all → BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `feat(ui): formatDateLong, formatCurrency and a shared parseHexColor in core/ui (KMP Phase 5)`.

### Task 2: `features/wardrobe` as a KMP module (all code in `androidMain`); strings to Compose resources

**Files:**
- **Rewrite `features/wardrobe/build.gradle.kts`** by copying `features/chat/build.gradle.kts`.
  - Namespace `com.closet.features.wardrobe`, with `androidResources { enable = true }`. Add `compose.resources { packageOfResClass = "com.closet.features.wardrobe.resources"; generateResClass = always }`.
  - **androidMain:** `work-runtime-ktx`, `koin-androidx-workmanager`, `palette-ktx`, `navigation-compose`, `activity-compose` (if the picker needs it explicitly) and, temporarily, `material-icons-extended`.
  - **commonMain:** what chat's has, plus `kotlinx-datetime`.
  - Drop `buildConfig = true`; Task 3 replaces `BuildConfig.DEBUG`. Until then, the androidMain code keeps compiling by reading `BuildFlags` via `koinInject()` at the one call site.
  - Apply the Skiko script.
- **Moves (`git mv`):**
  - every file under `src/main/kotlin` → `src/androidMain/kotlin`, and `src/main/res` → `src/androidMain/res`;
  - tests → `src/androidHostTest/**`, unchanged except for imports.
- **Strings:**
  - Move all `strings.xml` entries, including the 3 `plurals`, to `src/commonMain/composeResources/values/strings.xml`.
  - Apply the Global Constraints formatting rules, and fix every `\'` and `\"`.
  - Delete strings with no reference, checked with `git grep`.
  - Keep `colors.xml` and the two drawables in androidMain `res`; they are used by launcher shortcuts.
- **Call sites:** every `R.string`/`R.plurals` use → `Res.string`/`Res.plurals` with CMP `stringResource`/`pluralStringResource`, or `getString` in non-composable code. `ClothingFormViewModel`'s `errorMessage` fields become `StringResource?`.
- **Tests:** `src/jvmTest/.../WardrobeStringsTest.kt`:
  - `getPluralString(Res.plurals.wardrobe_worn_times, 1, 1) == "Worn 1 time"` and `(…, 3, 3) == "Worn 3 times"`;
  - `getPluralString(Res.plurals.brand_management_delete_blocked_message, 2, "Acme", 2) == "\"Acme\" is used by 2 items. Reassign them before deleting."`;
  - `getString(Res.string.bulk_wash_apply_error) == "Couldn't update some items. Please try again."`

**Interfaces:**
- **Produces** the internal `com.closet.features.wardrobe.resources.Res`.
- `FormState.errorMessage` and `ClothingFormUiState.errorMessage` are `StringResource?`.

- [ ] **Step 1:** Write the strings test.
- [ ] **Step 2:** Run `./gradlew :features:wardrobe:jvmTest` and expect a compile failure.
- [ ] **Step 3:** Implement.
  - `git grep -nE "R\.(string|plurals)" -- features/wardrobe/src` prints nothing.
- [ ] **Step 4:** Verify:
  - `./gradlew :features:wardrobe:jvmTest :features:wardrobe:testAndroidHostTest`;
  - `./gradlew test`, including `AppKoinGraphTest` and `GmsKoinTest`;
  - `./gradlew assembleFullDebug`;

  all → BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `refactor(wardrobe): features/wardrobe as a KMP module; strings and plurals to Compose resources (KMP Phase 5)`.

### Task 3: platform seams; ViewModels, form state, routes and Koin to `commonMain`

**Files:**
- **Create `src/commonMain/.../photo/ItemPhotoProcessor.kt`:**
  ```kotlin
  interface ItemPhotoProcessor {
      val isSegmentationSupported: Boolean
      val isCaptionSupported: Boolean
      /** Copies a picked image (platform URI string) into app storage; returns its relative path. */
      suspend fun importPhoto(source: String): String
      suspend fun deleteImage(relativePath: String)
      suspend fun ensureSegmentationModel()
      /** Background-removed copy saved as a new PNG; returns its relative path. Throws on failure. */
      suspend fun removeBackground(relativePath: String): String
      /** Nearest seeded colors for the image's dominant/vibrant/muted swatches; empty on any failure. */
      suspend fun extractColors(relativePath: String, available: List<ColorEntity>): List<ColorEntity>
      /** Caption, or null if the image can't be decoded. Throws if captioning fails. */
      suspend fun describe(relativePath: String): String?
  }
  ```
- **Create `src/androidMain/.../photo/AndroidItemPhotoProcessor.kt`** with constructor `(StorageRepository, SubjectSegmenter, CaptionEnrichmentProvider)`. Move the bodies verbatim from `ClothingFormViewModel`:
  - `BitmapUtils.decodeSampledBitmap(…, maxDim = 1024)`;
  - Palette swatches → `ColorMatcher.findNearestColor`;
  - `saveBitmap(masked, Uuid.random().toString())`;
  - `describe` decodes on IO, calls `captioner.describe` on `Dispatchers.Main`, and recycles the bitmap in `finally`.
- **Create `src/commonMain/.../CategoryShortcutPinner.kt`:** `fun interface CategoryShortcutPinner { fun pin(categoryId: Long, label: String) }`. Move the old `ClosetViewModel` body verbatim into `src/androidMain/.../AndroidCategoryShortcutPinner.kt(context: Context)`.
- **Route readers:** `src/commonMain/.../WardrobeArgs.kt` declares `internal expect fun SavedStateHandle.closetInitialCategoryId(): Long?`, `detailItemId(): Long`, `editItemId(): Long?` and `addOpenCamera(): Boolean`.
  - Android actuals use `toRoute<…Destination>()` with today's try/catch semantics.
  - JVM/iOS actuals read keys with `get<…>("initialCategoryId" | "itemId" | "openCamera")`, the same as `OutfitBuilderArgs.jvm.kt`.
- **Moves (`git mv` androidMain → commonMain):**
  - the five ViewModels;
  - form/UI state types;
  - the `@Serializable` destination classes. Split them out of `WardrobeNavigation.kt` into `WardrobeDestinations.kt`, keeping the class names and packages.
  - `di/WardrobeKoinModule.kt` is split: commonMain `commonWardrobeModule` holds the five `viewModelOf`; androidMain `wardrobeModule` holds `includes(commonWardrobeModule)` plus the scheduler, worker, `single<ItemPhotoProcessor> { AndroidItemPhotoProcessor(get(), get(), get()) }` and `single<CategoryShortcutPinner> { AndroidCategoryShortcutPinner(androidContext()) }`.
- **ViewModel changes:**
  - `appContext`, `StorageRepository`, `SubjectSegmenter` and `CaptionEnrichmentProvider` are removed. In their place: `ItemPhotoProcessor`, `ImagePathResolver`, `ShortcutUsageReporter`, `CategoryShortcutPinner` and `BuildFlags`, as each ViewModel needs.
  - `resolveImagePath(path): String?` replaces `File?`.
  - `onImageSelected(source: String?)` replaces `Uri?`.
  - `purchaseDate: kotlinx.datetime.LocalDate?`.
  - `ClothingDetailViewModel` exposes `val showDebugCard: Boolean = buildFlags.isDebug`.
  - Timber → Kermit.
  - Keep every guard, `NonCancellable` cleanup and stale-path check.
- **androidMain screens:** update the call sites (picker URI → `uri.toString()`, `File` → path, the debug flag from the ViewModel).
- **Tests:**
  - **`src/jvmTest/.../ClothingFormViewModelTest.kt`:** port the 4 existing tests to jvmTest with `kotlin.test`, and add the Review Focus 1 cases with a hand-written `FakeItemPhotoProcessor` that records calls and can be set to fail.
  - **`BulkWashViewModelTest`:** `laundry_day` is reported after a successful apply.
  - **`ClosetViewModelTest`:** the pin delegates `(id, label)`.
  - **`src/jvmTest/.../di/WardrobeJvmKoinGraphTest.kt`.**
  - **Note:** `ClothingFormViewModelTest` also checks that `quick_add` is reported after a successful add.

**Interfaces:**
- **Consumes:** Task 2's `Res`/`StringResource` state, plus the existing `ImagePathResolver`, `ShortcutUsageReporter` (`com.closet.core.data.shortcuts`) and `BuildFlags`.
- **Produces:** `ItemPhotoProcessor`, `CategoryShortcutPinner`, `commonWardrobeModule`, and the commonMain destinations (same FQNs).

- [ ] **Step 1:** Write the failing tests.
- [ ] **Step 2:** Run `./gradlew :features:wardrobe:jvmTest` and expect a compile failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Verify:
  - `./gradlew :features:wardrobe:jvmTest :features:wardrobe:testAndroidHostTest :features:wardrobe:compileCommonMainKotlinMetadata`;
  - the wardrobe iOS main and test compiles;
  - `git grep -nE "^import (android\.|java\.|timber|androidx\.(core|navigation|work|palette|activity))" -- features/wardrobe/src/commonMain` prints nothing;
  - `./gradlew test`.
- [ ] **Step 5:** Commit: `refactor(wardrobe): photo pipeline and shortcut seams; ViewModels, routes and Koin in commonMain (KMP Phase 5)`.

### Task 4: Material Symbols; screens to `commonMain`; PR checkpoint

**Files:**
- **Icons:** replace all 24 distinct `Icons.*` with `painterResource(CoreRes.drawable.ic_symbol_*)`, keeping tint and size.
  - **New drawables in `core/ui`:** create only those that don't already exist (check `core/ui/src/commonMain/composeResources/drawable/`).
    - `arrow_back` and `keyboard_arrow_right` (both autoMirrored)
    - `straighten`, `settings`, `favorite` and `favorite_filled`, `sort`, `savings`, `push_pin`, `palette`, `numbers`, `image`, `filter_list`, `content_copy`, `calendar_month`, `attach_money`, `add_a_photo`
    - `check` only if it differs from `check_circle`
  - Remove `material-icons-extended` from the build and, if unused anywhere else, from the version catalog.
- **`expect` UI hooks (wardrobe commonMain, with actuals per platform):**
  - `@Composable internal expect fun rememberPhotoPicker(onPicked: (String?) -> Unit): () -> Unit`. Android uses `PickVisualMedia(ImageOnly)` → `uri?.toString()`; JVM/iOS return a no-op launcher.
  - `@Composable internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)`. Android uses activity `BackHandler`; JVM/iOS are no-ops.
  - `internal expect fun plainTextClipEntry(text: String): ClipEntry`. Android: `ClipEntry(ClipData.newPlainText("text", text))`; JVM: `ClipEntry(StringSelection(text))`; iOS: `ClipEntry.withPlainText(text)`.
- **Dates and money:**
  - `FormComponents`' date picker uses `utcMillisToLocalDate` and `localDateToUtcMillis` (Review Focus 2) in a new `src/commonMain/.../PurchaseDates.kt`.
  - The detail screen uses `formatCurrency`, `formatMonthYear` and `formatDateLong`.
  - `MultiSelectSheet` uses the core/ui `parseHexColor`.
- **Previews:** move all `@Preview`s and their `Configuration` imports into androidMain (`ClothingDetailPreviews.kt` and `FormPreviews.kt` already exist; add `WardrobePreviews.kt` for the rest), making previewed Composables `internal` where they're `private`.
- **Moves (`git mv` androidMain → commonMain):** every screen and component, including `SemanticDebugCard`. Left in androidMain: `WardrobeNavigation.kt`, the previews, `BatchSegmentationWorker`, `repository/**`, `di/WardrobeNoOpModule.kt`, androidMain `wardrobeModule`, the Android processor and pinner, and the `expect` actuals.
- **Docs and changelog:**
  - **CLAUDE.md:**
    - the module list (`features/wardrobe` is KMP);
    - the Kermit and Compose-resources lists (and the "not-yet-migrated" sentence: none remain);
    - the no-op-stub wording (`features/wardrobe` stubs stay in `androidMain` because they're `Bitmap`-typed);
    - the full-verification command (add `:features:wardrobe:jvmTest :features:wardrobe:compileCommonMainKotlinMetadata`);
    - the Logging line (Timber is now only in `app`).
  - **`app-android/CHANGELOG.md`** `[Unreleased]` → `### Changed`: "**Wardrobe is Kotlin Multiplatform** (KMP Phase 5): shared closet, item detail, add/edit form, bulk wash and brand screens, Compose resources, Material Symbols icons. Every feature is now multiplatform."
- **Tests:** `src/commonTest/.../PurchaseDatesTest.kt` (Review Focus 2).

- [ ] **Step 1:** Write `PurchaseDatesTest`.
- [ ] **Step 2:** Run `./gradlew :features:wardrobe:jvmTest` and expect a compile failure.
- [ ] **Step 3:** Implement.
  - `git grep -nE "material\.icons|@Preview|android\.|java\." -- features/wardrobe/src/commonMain` prints nothing.
  - `git grep -n "material.icons" -- '*.kt' '*.kts' '*.toml'` prints nothing, or only entries you can justify.
- [ ] **Step 4:** Verify:
  - the full CLAUDE.md verification command (in separate invocations if needed) → BUILD SUCCESSFUL;
  - the wardrobe and core/ui iOS main and test compiles;
  - `unzip -l` on both debug APKs lists `composeResources/com.closet.features.wardrobe.resources/` and the new `ic_symbol_*`.
- [ ] **Step 5:** Commit: `refactor(wardrobe): Material Symbols; wardrobe UI in commonMain (KMP Phase 5)`.
- [ ] **Step 6:** PR checkpoint (the controller does this).
  - Push `feat/kmp-phase5-wardrobe`, open the PR (closes nothing; reference #97), and wait for CI.
  - Smoke-test on the **test device only**, both flavors, with the demo wardrobe:
    - **Closet:** the grid shows photos and "Worn N time(s)" plurals; filters, sort, favorites, show-archived and the category chips work. The category "pin shortcut" action (whatever triggers `ClosetViewModel`'s pin today) shows the launcher prompt. Cancel it; never add a shortcut to the device.
    - **Item detail:** price and cost-per-wear show in local currency, the purchase month and the wear-log dates are localized, favorite toggles, and delete asks for confirmation. The debug card shows in debug builds and Copy works.
    - **Add item:**
      - pick a photo → colors are auto-tagged;
      - full: Remove background → the photo swaps and colors update; Revert restores it;
      - set a purchase date and save; re-open edit → the same date.
    - **Back with unsaved changes:** the discard prompt appears.
    - **Bulk wash:** select → "N selected" plural → Mark Clean applies.
    - **Brands:** add, rename, and a blocked delete shows the plural message.
    - **Foss:** no Remove background button, and no crash.

    Wait for the owner to merge.
