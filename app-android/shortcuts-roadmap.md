# Android App Shortcuts — Implementation Roadmap

Tracks the addition of static, dynamic, and pinned shortcuts to hangr (Android).
API reference: `ShortcutManagerCompat` (androidx.core, already on classpath via core-ktx).

> **Cap:** Never exceed **4 static + dynamic shortcuts** combined. Launchers rarely surface more.
> **Label limits:** `shortLabelRes` ≤ 10 chars. `longLabelRes` ≤ 25 chars.

---

## Phase 1 — Intent routing infrastructure ✅

The app uses a single `MainActivity` with Compose Navigation. Shortcuts must encode their
target destination as an intent extra so `MainActivity` can drive the `NavController` to the
right screen on cold-start and on re-launch (`onNewIntent`).

- [x] **§1.1 — Define shortcut action constants**
  Created `app/src/main/kotlin/com/closet/shortcuts/ShortcutActions.kt`.
  Also added `ID_*` shortcut ID constants alongside the action strings, used by
  `reportShortcutUsed()` calls in Phase 5.

- [x] **§1.2 — Handle shortcut intents in `MainActivity`**
  Added `_shortcutIntent: MutableStateFlow<Intent?>` + `onNewIntent` override.
  Intent is published only when `savedInstanceState == null` (fresh start) to prevent
  re-navigation on rotation. `ClosetNavGraph` receives the flow and an `onShortcutConsumed`
  callback that resets it to `null`.

- [x] **§1.3 — Consume shortcut intents in `ClosetNavGraph`**
  `LaunchedEffect(pendingIntent)` routes each action via `collectAsStateWithLifecycle()`:
  | Action | Destination | Notes |
  |--------|-------------|-------|
  | `ACTION_QUICK_ADD` | `AddClothingDestination` | Camera pre-select wired in Phase 4 |
  | `ACTION_LOG_FIT` | `OutfitBuilderDestination(-1L)` | ✅ fully wired |
  | `ACTION_LAUNDRY_DAY` | *(placeholder)* | Destination added in Phase 3 |
  | `ACTION_CATEGORY` | `ClosetDestination` | Category filter wired in Phase 6 |

---

## Phase 2 — Static shortcuts (`shortcuts.xml`) ✅

Static shortcuts are declared in XML, shipped with the APK, and always visible to launchers.

- [x] **§2.1 — Create `shortcuts.xml`**
  Create `app/src/main/res/xml/shortcuts.xml`.
  Declare **3 static shortcuts** (leaving 1 slot for dynamic/pinned use):

  ```xml
  <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">

      <!-- Shortcut 1: Quick Add -->
      <shortcut
          android:shortcutId="quick_add"
          android:enabled="true"
          android:icon="@drawable/ic_shortcut_add"
          android:shortcutShortLabel="@string/shortcut_quick_add_short"
          android:shortcutLongLabel="@string/shortcut_quick_add_long">
          <intent
              android:action="com.closet.shortcut.QUICK_ADD"
              android:targetPackage="com.closet"
              android:targetClass="com.closet.MainActivity" />
          <categories android:name="android.shortcut.conversation" />
      </shortcut>

      <!-- Shortcut 2: Log Today's Fit -->
      <shortcut
          android:shortcutId="log_fit"
          android:enabled="true"
          android:icon="@drawable/ic_shortcut_outfit"
          android:shortcutShortLabel="@string/shortcut_log_fit_short"
          android:shortcutLongLabel="@string/shortcut_log_fit_long">
          <intent
              android:action="com.closet.shortcut.LOG_FIT"
              android:targetPackage="com.closet"
              android:targetClass="com.closet.MainActivity" />
          <categories android:name="android.shortcut.conversation" />
      </shortcut>

      <!-- Shortcut 3: Laundry Day -->
      <shortcut
          android:shortcutId="laundry_day"
          android:enabled="true"
          android:icon="@drawable/ic_shortcut_laundry"
          android:shortcutShortLabel="@string/shortcut_laundry_short"
          android:shortcutLongLabel="@string/shortcut_laundry_long">
          <intent
              android:action="com.closet.shortcut.LAUNDRY_DAY"
              android:targetPackage="com.closet"
              android:targetClass="com.closet.MainActivity" />
          <categories android:name="android.shortcut.conversation" />
      </shortcut>

  </shortcuts>
  ```

- [x] **§2.2 — Register `shortcuts.xml` in the manifest**
  File: `app/src/main/AndroidManifest.xml`
  Add `<meta-data>` inside the `<activity>` block for `MainActivity`:
  ```xml
  <meta-data
      android:name="android.app.shortcuts"
      android:resource="@xml/shortcuts" />
  ```

- [x] **§2.3 — Add string resources**
  File: `app/src/main/res/values/strings.xml`
  All short labels must be ≤ 10 characters:
  ```xml
  <!-- Shortcut labels -->
  <string name="shortcut_quick_add_short">Quick Add</string>   <!-- 9 chars ✓ -->
  <string name="shortcut_quick_add_long">Add item to wardrobe</string>
  <string name="shortcut_log_fit_short">Log Fit</string>       <!-- 7 chars ✓ -->
  <string name="shortcut_log_fit_long">Log today\'s outfit</string>
  <string name="shortcut_laundry_short">Laundry</string>       <!-- 7 chars ✓ -->
  <string name="shortcut_laundry_long">Bulk mark wash status</string>
  <string name="shortcut_category_short">Category</string>     <!-- 8 chars ✓ -->
  <string name="shortcut_category_long">Browse category</string>
  ```

- [x] **§2.4 — Add shortcut icons**
  No new drawables needed. `core/ui` already has exact matches and library resources
  merge into the final APK, so `shortcuts.xml` can reference them directly:
  - Quick Add → `@drawable/ic_icon_coat_hanger`
  - Log Fit → `@drawable/ic_icon_t_shirt`
  - Laundry Day → `@drawable/ic_icon_washing_machine`
  - Category (Phase 6) → `@drawable/ic_icon_dresser`

---

## Phase 3 — New screen: Bulk Wash / Laundry Day ✅

The "Laundry Day" shortcut needs a screen that doesn't exist yet.

- [x] **§3.1 — Add `BulkWashDestination` route**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/WardrobeNavigation.kt`
  ```kotlin
  @Serializable
  object BulkWashDestination
  ```

- [x] **§3.2 — Create `BulkWashViewModel`**
  New file: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/BulkWashViewModel.kt`
  - Expose `StateFlow<List<ClothingItemWithMeta>>` from `ClothingRepository.getAllItems()`.
  - Expose a `selectedIds: Set<Long>` toggle.
  - `fun applyWashStatus(status: WashStatus)` — calls
    `ClothingRepository.updateWashStatus(id, status)` for each selected ID.

- [x] **§3.3 — Create `BulkWashScreen`**
  New file: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/BulkWashScreen.kt`
  - Lazy list of all items with checkboxes.
  - Bottom bar with "Mark Clean" / "Mark Dirty" / "Archive" action buttons.
  - Destructive actions (Archive) require an `AlertDialog` confirmation
    (follow the existing delete pattern in `ClothingDetailScreen`).

- [x] **§3.4 — Register `BulkWashDestination` in the nav graph**
  File: `app/src/main/kotlin/com/closet/navigation/ClosetNavGraph.kt`
  Wire `composable<BulkWashDestination>` → `BulkWashScreen(onBack = ...)`.

---

## Phase 4 — Quick Add camera pre-selection ✅

The "Quick Add" shortcut should open the form with the image picker / camera already
triggered, skipping the empty form state.

- [x] **§4.1 — Add `openCamera: Boolean` param to `AddClothingDestination`**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/WardrobeNavigation.kt`
  Changed from `object` to `data class AddClothingDestination(val openCamera: Boolean = false)`.

- [x] **§4.2 — Consume `openCamera` in `ClothingFormViewModel`**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/ClothingFormViewModel.kt`
  - Added `addDestination = savedStateHandle.toRoute<AddClothingDestination>()` (try/catch,
    same pattern as `editDestination`).
  - In `init {}`, if `addDestination?.openCamera == true`, launches a coroutine that sends
    `ClothingFormEvent.OpenImagePicker` on the existing event channel.
  - Added `object OpenImagePicker : ClothingFormEvent()` to the sealed class.

- [x] **§4.3 — Handle the event in `ClothingFormScreen`**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/ClothingFormScreen.kt`
  - Moved `rememberLauncherForActivityResult` above `LaunchedEffect(Unit)` so the launcher
    is in scope for the event handler.
  - Added `ClothingFormEvent.OpenImagePicker` branch: calls
    `launcher.launch(PickVisualMediaRequest(ImageOnly))`.

---

## Phase 5 — `reportShortcutUsed()` instrumentation ✅

Android learns shortcut habits from usage signals. Call `ShortcutManagerCompat.reportShortcutUsed()`
at the moment the user completes the action the shortcut was designed for.

- [x] **§5.1 — Report after outfit is logged**
  File: `features/outfits/src/main/kotlin/com/closet/features/outfits/JournalViewModel.kt`
  Injected `@ApplicationContext appContext: Context`. In `logOutfitOnDate()`, calls
  `ShortcutManagerCompat.reportShortcutUsed(appContext, "log_fit")` on `DataResult.Success`.

- [x] **§5.2 — Report after background removal completes**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/ClothingFormViewModel.kt`
  Injected `@ApplicationContext appContext: Context`. In `removeBackground()`, calls
  `ShortcutManagerCompat.reportShortcutUsed(appContext, "quick_add")` after the PNG is
  saved and `_form` is updated with `imagePath = savedPath`.

- [x] **§5.3 — Report after bulk wash status is applied**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/BulkWashViewModel.kt`
  Injected `@ApplicationContext appContext: Context`. In `applyWashStatus()`, calls
  `ShortcutManagerCompat.reportShortcutUsed(appContext, "laundry_day")` after all row
  updates complete, before `clearSelection()`.

---

## Phase 6 — Pinned shortcuts: Category lookbooks ✅

Pinned shortcuts let users pin a specific category (e.g. "Gym Clothes") to their launcher.
These are dynamic — created at runtime from DB data.

- [x] **§6.1 — Add a "Pin to home screen" action in the Closet filter UI**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/ClosetScreen.kt`
  Added `onPinCategory: (Long, String) -> Unit` parameter to `ClosetContent` and
  `CategoryFilterRow`. Each named category chip is wrapped in a `Row` with a 32dp
  `IconButton(PushPin)` trailing it. Tapping the pin calls the ViewModel action.

- [x] **§6.2 — Add `pinCategoryShortcut(categoryId: Long, categoryName: String)` to `ClosetViewModel`**
  File: `features/wardrobe/src/main/kotlin/com/closet/features/wardrobe/ClosetViewModel.kt`
  Injected `@ApplicationContext appContext: Context`. Uses `ShortcutInfoCompat.Builder` with
  `setClassName(packageName, "com.closet.MainActivity")` and inline action/extra strings
  (avoids a `features/wardrobe` → `app` module dependency). Icon is
  `R.drawable.ic_shortcut_category` from the wardrobe module.

- [x] **§6.3 — Add `ic_shortcut_category.xml`**
  Created `features/wardrobe/src/main/res/drawable/ic_shortcut_category.xml` — adaptive icon
  (amber background + `ic_icon_dresser` foreground + monochrome layer). Placed in
  `features/wardrobe` so `ClosetViewModel` can reference it without a reverse module dep.
  Also created `features/wardrobe/src/main/res/values/colors.xml` with `shortcut_icon_bg`
  (#F59E0B) to support the adaptive icon background reference.

- [x] **§6.4 — Filter the Closet screen by `categoryId` when launched from shortcut**
  `ClosetDestination` changed from `object` to
  `data class ClosetDestination(val initialCategoryId: Long? = null)`.
  `ClosetViewModel` now accepts `SavedStateHandle` and initialises `_selectedCategoryId`
  from `savedStateHandle.toRoute<ClosetDestination>().initialCategoryId` in its constructor.
  `ClosetNavGraph` extracts `EXTRA_CATEGORY_ID` from the shortcut intent and navigates to
  `ClosetDestination(initialCategoryId = categoryId)`.

---

## Phase 7 — Shortcut lifecycle maintenance

Shortcuts must be disabled when the content they reference no longer exists.

- [ ] **§7.1 — Disable category shortcuts on category deletion**
  If categories become deletable in future: call
  `ShortcutManagerCompat.disableShortcuts(context, listOf("category_$categoryId"), reason)`
  from `ClothingRepository` or a dedicated `ShortcutRepository`.

- [ ] **§7.2 — Keep dynamic shortcut count ≤ 4 total (static + dynamic)**
  Since 3 static shortcuts are declared, there is **1 remaining slot** for dynamic shortcuts.
  If you add any dynamic shortcuts later (e.g. "recently viewed item"), add a guard:
  ```kotlin
  val current = ShortcutManagerCompat.getDynamicShortcuts(context)
  if (current.size >= ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)) {
      // remove LRU before adding
  }
  ```

---

## Files summary

| Status | File | Change |
|--------|------|--------|
| ✅ Done | `app/src/main/kotlin/com/closet/shortcuts/ShortcutActions.kt` | Action/extra/ID constants |
| ✅ Done | `app/src/main/kotlin/com/closet/MainActivity.kt` | `onNewIntent` + shortcut flow |
| ✅ Done | `app/src/main/kotlin/com/closet/navigation/ClosetNavGraph.kt` | Shortcut intent routing |
| ✅ Done | `app/src/main/res/xml/shortcuts.xml` | Static shortcut declarations |
| ✅ Done | `app/src/main/AndroidManifest.xml` | Register shortcuts.xml meta-data |
| ✅ Done | `app/src/main/res/values/strings.xml` | Shortcut label strings |
| *(n/a)* | ~~New shortcut icon drawables~~ | Reused existing `core/ui` vectors |
| ✅ Done | `features/wardrobe/src/main/kotlin/.../BulkWashViewModel.kt` | Bulk status ViewModel |
| ✅ Done | `features/wardrobe/src/main/kotlin/.../BulkWashScreen.kt` | Laundry Day UI |
| ✏️ Modify | `features/wardrobe/src/main/kotlin/.../WardrobeNavigation.kt` | `BulkWashDestination`, `AddClothingDestination(openCamera)` |
| ✏️ Modify | `features/wardrobe/src/main/kotlin/.../ClothingFormViewModel.kt` | `openCamera` param + `OpenImagePicker` event |
| ✏️ Modify | `features/wardrobe/src/main/kotlin/.../ClothingFormScreen.kt` | Consume `OpenImagePicker` event |
| ✏️ Modify | `features/wardrobe/src/main/kotlin/.../ClosetViewModel.kt` | `pinCategoryShortcut()`, `initialCategoryId` filter |
| ✏️ Modify | `features/wardrobe/src/main/kotlin/.../ClosetScreen.kt` | Pin button on category chips |
| ✏️ Modify | `features/outfits/src/main/kotlin/.../JournalViewModel.kt` | `reportShortcutUsed("log_fit")` |
