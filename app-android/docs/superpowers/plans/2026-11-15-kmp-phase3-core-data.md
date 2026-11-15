# KMP Phase 3: `core/data` → Kotlin Multiplatform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `core/data` into a KMP module (Android, JVM, iosArm64, iosSimulatorArm64) whose database, repositories, preferences, HTTP clients and platform interfaces live in `commonMain`, with Room tests on the JVM and no behavior change on Android.

**Architecture:** First convert the build with all code in `androidMain` (Task 1). Then move code into `commonMain` in dependency order: logging (Kermit), pure Kotlin, dates (`java.time` → `kotlin.time`/`kotlinx-datetime`), Room KMP, DataStore + `SecretStore`, Ktor + weather + `LocationProvider`, and finally `EmbeddingEncoder` and the work schedulers. Android keeps every existing implementation in `androidMain`. JVM and iOS get compiling stubs, which the spec allows in this phase. Delivered as **three PRs** to `main`, each merged before the next starts: **PR A** = Tasks 1–3, **PR B** = Tasks 4–6, **PR C** = Tasks 7–9.

**Tech Stack:** Kotlin 2.4.21, AGP 9.1 + `com.android.kotlin.multiplatform.library`, Room 2.8.4 KMP + `androidx.sqlite:sqlite-bundled` 2.6.2, DataStore Preferences KMP 1.1.4, Ktor 3.1.3 (OkHttp / Darwin engines), Kermit 2.2.0, `kotlinx-datetime`, Koin 4.2.2.

**Spec:** `docs/superpowers/specs/2026-11-14-kmp-migration-design.md`: "Database", "Images and files", "Embeddings", "Platform gaps", "Logging", "Build and CI", "Conventions borrowed from MediaTracker", "Android-only dependencies and their fate", and Phases item 3. Toolchain: `docs/superpowers/notes/kmp-toolchain.md` (plugins, Room KMP setup, task names, klib ABI check).

## Global Constraints

- Run Gradle from `app-android/`. Git paths are relative to `app-android/`.
- **Android behavior is unchanged.** Same database file (`closet.db` at `Context.getDatabasePath`), same DataStore files (`ai_prefs`, `weather_prefs`), same `EncryptedSharedPreferences` file, same stored text formats, same user-visible strings. The schema stays version 1, and `1.json`'s identity hash must not change.
- **Targets:** `android { namespace = "com.closet.core.data"; compileSdk = 36; minSdk = 26 }` (keep the module's current values if they differ), `jvm()`, `iosArm64()`, `iosSimulatorArm64()`. Use the plugin setup in `kmp-toolchain.md` (the `android {}` DSL, not the deprecated `androidLibrary {}`; `-Xexpect-actual-classes`; JVM target 17).
- **Nothing platform-specific in `commonMain`:** no `java.*`, `android.*`, `androidx.work`, `security-crypto`, ONNX Runtime, `Context` or `Bitmap`. `:core:data:compileCommonMainKotlinMetadata` must pass after every task. That task is the local stand-in for the iOS compile, which only runs in CI.
- **Klib ABI:** every new KMP dependency's iOS klibs must be ABI ≤ 2.4.0. Check with the method in `kmp-toolchain.md` before adding one. If a library's latest release is too new, use its newest release that passes.
- **No `BuildConfig` in `core/data`** (the KMP Android library plugin has none). The debug flag comes from `app` through `BuildFlags` (Task 1).
- **Stubs:** the spec allows JVM/iOS stubs in this phase. A stub is honest: a capability reports itself unavailable (`isAvailable = false`, a `null` location, a no-op scheduler, an in-memory secret store). It never fakes success.
- **Cancellation:** keep every existing `CancellationException` rethrow. New code uses `catchingNonCancellation` (Task 3). *Ruling:* moved code keeps its existing explicit rethrows instead of being rewritten to the helper. Rewriting every catch during a move makes the move unreviewable, and adopting the helper everywhere is a follow-up sweep. This is a deviation from the spec's "required in new or moved code".
- **Out of scope for Phase 3** (moved by Phase 5 with their first common consumer): `StorageRepository`, `CaptionEnrichmentProvider`, `BitmapUtils`, `PaletteUtil`, `ColorMatcher` and the workers themselves. Their APIs are built on `Uri`/`Bitmap` and all their callers are Android UI or WorkManager code. *Ruling:* this also defers the spec's `ImageProcessor` interface to Phase 5, because nothing in common code needs it yet. `core/ui` (Phase 4) and the features (Phase 5) stay Android-only modules; they only see `core/data`'s Android variant.
- **Verification command** (from Task 1 on): `./gradlew test :core:data:jvmTest :core:data:testAndroidHostTest :core:data:compileCommonMainKotlinMetadata lint assembleFullDebug assembleFossDebug`. `./gradlew test` alone does not run `jvmTest`.
- **PR gate:** after the last task of each PR (3, 6, 9), push and open the PR. The `ios-compile` CI job must pass, and its `iosCompileCheck` now includes `:core:data`. A device smoke test on both flavors must pass. Then wait for the owner to merge before starting the next PR's tasks.

## Review Focus

1. **Existing data survives the upgrade on Android.** A user's closet, AI settings and weather settings are still there after updating: the database file and both DataStore files resolve to the same paths as before. → Task 5 (database path test) and Task 7 (DataStore path test).
2. **Stored timestamps keep their exact text.** Rows written by the `java.time` build must still parse, and new rows must sort the same way. → Task 4, converter compatibility test.
3. **API keys never reach logs.** `Authorization`, `x-api-key` and `x-goog-api-key` header values stay redacted after the HTTP client builder moves to common code. → Task 8, redaction test.
4. **A duplicate brand name is still a friendly error.** `BrandRepository` relied on Android's `SQLiteConstraintException`. Under the bundled driver it must still return `AppError.DatabaseError.ConstraintViolation`, not `QueryError`. → Task 6, jvmTest.
5. **Release builds log nothing.** With `BuildFlags(isDebug = false)`, Kermit has no writers and HTTP logging is `NONE`. → Task 2, logging test.

---

### Task 1: Convert the build to KMP, with all code in `androidMain`

**Files:**
- Modify: `core/data/build.gradle.kts` (rewrite), root `build.gradle.kts` (plugins `apply false` if missing), `gradle/libs.versions.toml`
- Move (git mv): `core/data/src/main/kotlin/**` → `core/data/src/androidMain/kotlin/**`; `src/main/assets` → `src/androidMain/assets`; `src/main/AndroidManifest.xml` (if any) → `src/androidMain/`; `src/test/**` → `src/androidHostTest/**`; `src/androidTest/**` → `src/androidDeviceTest/**`
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/BuildFlags.kt`
- Modify: `core/data/.../di/DataKoinModule.kt` (replace `BuildConfig.DEBUG`), `app/src/main/kotlin/com/closet/di/AppKoinModule.kt` (provide `BuildFlags`), every Koin test that builds `dataModule` without `appModule` (`DataKoinModuleTest`, `SingletonParityTest`, the feature Koin tests, `GmsKoinTest`: add `single { BuildFlags(isDebug = false) }` to their overrides), `app-android/CLAUDE.md` (Commands section)

**Interfaces:**
- Produces: `data class BuildFlags(val isDebug: Boolean)` in `com.closet.core.data`, bound in `appModule` as `single { BuildFlags(BuildConfig.DEBUG) }`. Everything in `core/data` that read `BuildConfig.DEBUG` reads `get<BuildFlags>().isDebug` instead.
- Produces: source sets `commonMain`, `androidMain`, `jvmMain`, `iosMain`, `commonTest`, `jvmTest`, `androidHostTest`, `androidDeviceTest`.

- [ ] **Step 1: Rewrite `core/data/build.gradle.kts`** with the toolchain-note plugins (`kotlin.multiplatform`, `android.kotlin.multiplatform.library`, `kotlin.serialization`, `ksp`, `room`), the four targets, `withHostTestBuilder {}` and `withDeviceTestBuilder {}` (instrumentation runner `androidx.test.runner.AndroidJUnitRunner`), and Android resources/assets enabled so the ONNX model and `vocab.txt` still package. Move every current dependency into `androidMain.dependencies` (test deps into `androidHostTest` / `androidDeviceTest`). Room: `room { schemaDirectory("$projectDir/schemas") }` and `add("kspAndroid", libs.room.compiler)` only; the other targets get KSP in Task 5. Keep the `androidDeviceTest` assets source pointing at `$projectDir/schemas` (`FreshInstallSchemaTest` reads `1.json` from there).
- [ ] **Step 2: Move the sources** as listed (git mv). No code changes except `BuildFlags`.
- [ ] **Step 3: Add `BuildFlags`**, bind it in `appModule`, replace `BuildConfig.DEBUG` in `core/data`, and add the `BuildFlags` override to the tests listed under Files. If the module used `BuildConfig` for anything else, list it in the report and stop with NEEDS_CONTEXT.
- [ ] **Step 4: Verify**
  Run: `./gradlew :core:data:testAndroidHostTest :core:data:jvmTest :core:data:compileCommonMainKotlinMetadata test lint assembleFullDebug assembleFossDebug`
  Expected: BUILD SUCCESSFUL. `jvmTest` has no tests yet, and that's fine. Host-test counts match the old `:core:data:testDebugUnitTest` (44).
  Run: `./gradlew :core:data:connectedAndroidDeviceTest` (or the task name the plugin creates; see `./gradlew :core:data:tasks --all | grep -i device`)
  Expected: 26/26 pass.
  Run: `unzip -l app/build/outputs/apk/full/debug/app-full-debug.apk | grep -E "arctic-embed-xs-q8.onnx|vocab.txt"`
  Expected: both assets are present.
- [ ] **Step 5: Update `CLAUDE.md` → Commands:** `core/data` tests are `:core:data:testAndroidHostTest` (unit), `:core:data:jvmTest` (Room/JVM; not run by `./gradlew test`), and the device test task from Step 4. Add the full verification command from Global Constraints.
- [ ] **Step 6: Commit** — `build(data): convert core/data to KMP (android, jvm, ios) with code in androidMain`

---

### Task 2: Timber → Kermit in `core/data`

**Files:**
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/logging/Logging.kt`; test `core/data/src/commonTest/kotlin/com/closet/core/data/logging/LoggingTest.kt`
- Modify: every `core/data` file that imports `timber.log.Timber`; `app/src/main/kotlin/com/closet/ClosetApp.kt`; `gradle/libs.versions.toml` (Kermit 2.2.0); `core/data/build.gradle.kts` (Kermit in `commonMain`, Timber removed from `core/data`)

**Interfaces:**
- Produces: `fun configureLogging(flags: BuildFlags)` in `com.closet.core.data.logging`. Debug sets Kermit's platform default writer and minimum severity `Verbose`. Release sets no writers (`Logger.setLogWriters(emptyList())`).
- `core/data` logs through `co.touchlab.kermit.Logger`. `Timber.tag("X").d(msg)` → `Logger.withTag("X").d { msg }`; `Timber.e(t, msg)` → `Logger.e(t) { msg }`. Keep every message text and tag. Where Timber derived the tag from the class name, pass that class's simple name explicitly.
- `ClosetApp.onCreate()` calls `configureLogging(BuildFlags(BuildConfig.DEBUG))` right after `startKoin`. The Timber `DebugTree` stays, because features still use Timber until Phase 5.

- [ ] **Step 1: Write `LoggingTest` (commonTest)**

```kotlin
@Test fun releaseHasNoWriters() {
    configureLogging(BuildFlags(isDebug = false))
    assertTrue(Logger.config.logWriterList.isEmpty())
}

@Test fun debugLogsVerbose() {
    configureLogging(BuildFlags(isDebug = true))
    assertTrue(Logger.config.logWriterList.isNotEmpty())
    assertEquals(Severity.Verbose, Logger.config.minSeverity)
}
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :core:data:jvmTest --tests "*LoggingTest"` → compile FAIL (`configureLogging` unresolved).
- [ ] **Step 3: Implement `configureLogging`, replace Timber across `core/data`, and wire `ClosetApp`.**
- [ ] **Step 4: Verify** — `grep -rn "timber" core/data/src core/data/build.gradle.kts` → no output; then the full verification command → BUILD SUCCESSFUL; `LoggingTest` passes on `jvmTest` and `testAndroidHostTest`.
- [ ] **Step 5: Commit** — `refactor(data): log with Kermit, debug-only via BuildFlags`

---

### Task 3: Move pure Kotlin to `commonMain`; add `catchingNonCancellation`

**Files:**
- Move (git mv) to `commonMain`: `ai/*.kt` (all 11 files), `util/AppError.kt`, `util/DataResult.kt`, `util/ItemVectorizer.kt`, `util/WordPieceTokenizer.kt`, `model/Enums.kt`
- Modify (stays in `androidMain` until Task 6, because it depends on `EmbeddingDao`): `util/EmbeddingIndex.kt`. Replace its `java.nio` blob decoding with the new pure-Kotlin helpers.
- Create: `core/data/src/commonMain/kotlin/com/closet/core/data/util/Catching.kt`, `.../util/EmbeddingBlobs.kt`
- Tests: move `androidHostTest/.../ai/ChatResponseParserTest.kt` → `commonTest` (replace any JUnit-only API with `kotlin.test`); create `commonTest/.../util/WordPieceTokenizerTest.kt`, `commonTest/.../util/EmbeddingBlobTest.kt`, `commonTest/.../util/CatchingTest.kt`
- Modify: `util/EmbeddingEncoder.kt` (androidMain) for the tokenizer's new constructor

**Interfaces:**
- Produces: `inline fun <T> catchingNonCancellation(block: () -> T): Result<T>`: like `runCatching`, but rethrows `CancellationException` (MediaTracker's pattern). Plus a `suspend` overload if a caller needs one.
- Produces: `WordPieceTokenizer(vocab: List<String>)`, which takes the vocab lines instead of an `InputStream`. `EmbeddingEncoder` (Android) reads the asset into lines and passes them.
- Produces: `fun ByteArray.toFloatArrayLE(): FloatArray` and `fun FloatArray.toByteArrayLE(): ByteArray` in `util`, little-endian IEEE-754, using `Float.fromBits`/`toRawBits`. Everything that decoded or encoded embedding blobs with `ByteBuffer` uses these. This includes `ItemEmbeddingEntity` helpers if it has any; keep those in place and only switch their implementation.
- `commonMain` can't see `androidMain`. If a listed file has a platform import, or references a type that is still in `androidMain` (an entity, a DAO result type, `DailyForecast`), leave it in `androidMain`, note it in the report, and the task that moves its dependency moves it too.

- [ ] **Step 1: Write the failing tests**
  - `EmbeddingBlobTest.roundTripsAndMatchesLittleEndianLayout`: `floatArrayOf(0f, 1f, -2.5f, Float.MIN_VALUE, 3.4028235e38f).toByteArrayLE().toFloatArrayLE()` contentEquals the input; `1f.toByteArrayLE()`-style check: `floatArrayOf(1f).toByteArrayLE()` equals `byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F)`.
  - `EmbeddingBlobTest.matchesJavaByteBuffer` (put in `jvmTest`, not commonTest): for a 384-float random array, `toByteArrayLE()` equals `ByteBuffer.allocate(384*4).order(LITTLE_ENDIAN).asFloatBuffer().put(arr)` bytes. This pins compatibility with blobs already stored.
  - `WordPieceTokenizerTest`: with vocab `listOf("[PAD]","[UNK]","[CLS]","[SEP]","hello","world","##s")`, `encode("Hello worlds", 8)` gives `inputIds` = `[2,4,5,6,3,0,0,0]` and `attentionMask` = `[1,1,1,1,1,0,0,0]`. A word not in the vocab maps to `[UNK]` (id 1).
  - `CatchingTest`: `catchingNonCancellation { error("x") }.isFailure`; `assertFailsWith<CancellationException> { catchingNonCancellation { throw CancellationException("c") } }`.
- [ ] **Step 2: Run them to verify they fail** — `./gradlew :core:data:jvmTest` → compile FAIL.
- [ ] **Step 3: Move the files, implement the helpers, and switch `EmbeddingIndex`, `EmbeddingEncoder` and `EmbeddingWorker` to the new blob and tokenizer APIs.**
- [ ] **Step 4: Verify** — the full verification command → BUILD SUCCESSFUL; the new tests pass on `jvmTest`, and the commonTest ones also pass under `testAndroidHostTest`; `ChatResponseParserTest` keeps its test count.
- [ ] **Step 5: Commit** — `refactor(data): move pure Kotlin (ai, tokenizer, index, results) to commonMain`
- [ ] **Step 6: PR A gate** (see Global Constraints): push, open the PR, `ios-compile` green, device smoke test on both flavors (launch, add an item, chat retrieves it after embedding or the existing debug path, Settings opens). Wait for merge.

---

### Task 4: Dates — `java.time` → `kotlin.time.Instant` / `kotlinx-datetime`

**Files:**
- Modify (core/data): `Converters.kt`; `model/ClothingItemEntity.kt`, `ItemEmbeddingEntity.kt`, `OutfitEntities.kt`, `DailyForecast.kt`, `CachedForecastEntry.kt`; `dao/OutfitDao.kt`; `repository/ClothingRepository.kt`, `LogRepository.kt`, `OutfitRepository.kt`; `weather/*Client.kt`; `worker/EmbeddingWorker.kt`; tests that use `java.time`
- Modify (consumers): every file outside `core/data` that passes or reads these types (survey: `app` 1, `features/chat` 3, `features/outfits` 5, `features/recommendations` 1, `features/stats` 2, `features/wardrobe` 5, `platform/gms` 2). Only the ones that touch `core/data` types need changing. A feature's own internal `java.time` use can stay until Phase 5.
- Modify: `gradle/libs.versions.toml` (`kotlinx-datetime`, the latest release passing the ABI rule), `core/data/build.gradle.kts` (`commonMain` dependency, exposed as `api`)
- Test: create `core/data/src/jvmTest/kotlin/com/closet/core/data/ConvertersCompatTest.kt`

**Interfaces:**
- `java.time.Instant` → `kotlin.time.Instant`; `Instant.now()` → `Clock.System.now()` (`kotlin.time.Clock`). `java.time.LocalDate` → `kotlinx.datetime.LocalDate`. `OffsetDateTime` parsing in a weather client → `kotlin.time.Instant.parse` or kotlinx-datetime's offset parsing, whichever keeps the same result. If the compiler requires `@OptIn(ExperimentalTime::class)`, add it module-wide via `compilerOptions.optIn` in each affected module, not per file.
- `Converters` keeps the same storage: `String?` ↔ `Instant?` through `toString()` / `Instant.parse()`.
- The model files still live in `androidMain` (Room moves in Task 5). This task only changes types. Moving the model files is Task 5's job.

- [ ] **Step 1: Write `ConvertersCompatTest` (jvmTest)**
  - `kotlinToStringMatchesJavaToString`: for instants at epoch seconds `0`, `1_763_000_000`, plus the nanos variants `0`, `282_000_000`, `282_100_000`, `123_456_789`, `Converters().dateToTimestamp(kotlinInstant)` equals `java.time.Instant.ofEpochSecond(s, n).toString()`.
  - `parsesStringsWrittenByJavaTime`: `fromTimestamp("2026-11-15T03:50:25.282Z")`, `"2026-11-15T03:50:25Z"` and `"2026-11-15T03:50:25.123456789Z"` round-trip to the same epoch seconds and nanos as `java.time.Instant.parse` of the same string.
  - `localDateIsoTextUnchanged`: `kotlinx.datetime.LocalDate(2026, 4, 4).toString() == "2026-04-04"`.
  If `kotlin.time.Instant.toString()` differs from Java for any case, don't change the test. Make `dateToTimestamp` format explicitly to Java's layout, and say so in the report.
- [ ] **Step 2: Run it to verify it fails** — `./gradlew :core:data:jvmTest --tests "*ConvertersCompatTest"` → compile FAIL (Converters still on `java.time`).
- [ ] **Step 3: Switch the types in `core/data` and update the consumers.**
- [ ] **Step 4: Verify** — the full verification command → BUILD SUCCESSFUL; `grep -rn "import java.time" core/data/src/androidMain core/data/src/commonMain` lists only files that stay in `androidMain` for Phase 3 (list them in the report); the device tests pass, including `StatsDaoTest`'s date filters.
- [ ] **Step 5: Commit** — `refactor(data): kotlin.time/kotlinx-datetime instead of java.time`

---

### Task 5: Room KMP in `commonMain`, bundled driver, Android builder

**Files:**
- Move (git mv) to `commonMain`: `ClothingDatabase.kt`, `Converters.kt`, `DatabaseSeeder.kt`, `dao/*.kt`, `model/*.kt`
- Create: `core/data/src/androidMain/kotlin/com/closet/core/data/DatabaseBuilder.android.kt`, `.../jvmMain/.../DatabaseBuilder.jvm.kt`, `.../iosMain/.../DatabaseBuilder.ios.kt`
- Modify: `core/data/build.gradle.kts` (Room runtime + `sqlite-bundled` in `commonMain`; `kspJvm`, `kspIosArm64`, `kspIosSimulatorArm64`); `repository/ClothingRepository.kt`, `OutfitRepository.kt`, `BrandRepository.kt` (transactions and constraint errors); `app/.../backup/BackupRepository.kt` and `RestoreRepository.kt` (call sites, if signatures change); `di/DataKoinModule.kt`
- Test: `androidDeviceTest/.../DatabasePathTest.kt` (new)

**Interfaces:**
- `ClothingDatabase` gets `@ConstructedBy(ClothingDatabaseConstructor::class)` with `expect object ClothingDatabaseConstructor : RoomDatabaseConstructor<ClothingDatabase>`. KSP generates the actuals.
- Produces (common): `fun RoomDatabase.Builder<ClothingDatabase>.configureClosetDatabase(): RoomDatabase.Builder<ClothingDatabase>`. It sets `BundledSQLiteDriver()`, `Dispatchers.IO` as the query coroutine context, and the callbacks. `onCreate(connection)` runs `PRAGMA foreign_keys = ON`, `DatabaseSeeder.seedAll(connection)` and the category triggers. `onOpen(connection)` runs `PRAGMA foreign_keys = ON` and `CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1`. The SQL is identical to today's.
- `DatabaseSeeder.seedAll(connection: SQLiteConnection)` (`androidx.sqlite`), with the same SQL. Statements with bound args go through `connection.prepare(sql).use { it.bindText(...); it.step() }`.
- Android: `ClothingDatabase.getDatabase(context: Context)` and `closeAndReset()` keep their signatures and singleton (androidMain). The builder is `Room.databaseBuilder<ClothingDatabase>(context, context.getDatabasePath("closet.db").absolutePath).configureClosetDatabase()`. JVM: `fun getDatabaseBuilder(file: File)`. iOS: `fun getDatabaseBuilder(): RoomDatabase.Builder<ClothingDatabase>` under `NSDocumentDirectory`. Neither is wired into Koin in this task.
- `checkpointWal()` and `databaseVersion` keep their signatures but become `suspend`, implemented with `useWriterConnection { it.execSQL("PRAGMA wal_checkpoint(FULL)") }` and `PRAGMA user_version`. Update their two callers in `app/backup`.
- `androidx.room.withTransaction { }` → `useWriterConnection { it.immediateTransaction { … } }`, or Room KMP's equivalent with the same atomicity.
- `BrandRepository`: replace `android.database.sqlite.SQLiteConstraintException` with `androidx.sqlite.SQLiteException` whose message contains `"UNIQUE constraint failed"` (or `"constraint failed"`, matching today's coverage) → `AppError.DatabaseError.ConstraintViolation`.

- [ ] **Step 1: Write `DatabasePathTest` (androidDeviceTest)** — `ClothingDatabase.getDatabase(context)`, run one write through a DAO, then assert `context.getDatabasePath("closet.db").exists()` and that `context.databaseList()` contains `"closet.db"`. Clean up with `closeAndReset()` and `deleteDatabase`. It passes before the change too; it's the regression guard for Review Focus 1.
- [ ] **Step 2: Move the files and implement the builders, callbacks, seeder and repository changes above.**
- [ ] **Step 3: Verify** — the full verification command → BUILD SUCCESSFUL; `:core:data:kspKotlinJvm` and the iOS KSP tasks run; `git diff --stat core/data/schemas` → no change (identity hash unchanged); the device tests pass (`FreshInstallSchemaTest`, `StatsDaoTest`, `OutfitRepositoryTest`, `DatabasePathTest`).
- [ ] **Step 4: Commit** — `refactor(data): Room KMP with the bundled SQLite driver; database in commonMain`

---

### Task 6: Room tests on the JVM; DAO-only repositories to `commonMain`

**Files:**
- Move (git mv) `androidDeviceTest/.../{FreshInstallSchemaTest,StatsDaoTest,OutfitRepositoryTest}.kt` → `jvmTest/...`. Adapt them to `getDatabaseBuilder(tempFile)` (JVM) and to reading `1.json` from `file("schemas/…/1.json")`; pass the path via a system property set in Gradle or a relative path from the module dir.
- Move (git mv) to `commonMain`: `repository/BrandRepository.kt`, `ClothingRepository.kt`, `LogRepository.kt`, `LookupRepository.kt`, `OutfitRepository.kt`, `RecommendationRepository.kt`, `StatsRepository.kt`, `util/EmbeddingIndex.kt`, plus any Task 3 file that stayed behind waiting on these types (whichever now have no platform imports; list any that can't move)
- Create: `jvmTest/.../BrandRepositoryTest.kt`
- Keep `androidDeviceTest/.../DatabasePathTest.kt` (it's Android-only by nature)

**Interfaces:**
- Consumes: Task 5's `getDatabaseBuilder(file: File)` and `configureClosetDatabase()`.
- `FreshInstallSchemaTest` keeps all five checks. On the JVM it opens the database through the same `configureClosetDatabase()` callbacks.

- [ ] **Step 1: Write `BrandRepositoryTest.duplicateNameIsConstraintViolation` (jvmTest):** insert brand "Nike" twice through `BrandRepository`; the second result is `DataResult.Error` whose `throwable is AppError.DatabaseError.ConstraintViolation`.
- [ ] **Step 2: Move the three tests and the repositories, then run** `./gradlew :core:data:jvmTest`. Expected: `FreshInstallSchemaTest` (5), `StatsDaoTest` (20), `OutfitRepositoryTest` (1) and `BrandRepositoryTest` (1) all pass. If `BrandRepositoryTest` fails, fix the Task 5 constraint mapping, not the test.
- [ ] **Step 3: Verify** — the full verification command → BUILD SUCCESSFUL; `:core:data:connectedAndroidDeviceTest` → only `DatabasePathTest` remains, and it passes.
- [ ] **Step 4: Update `CLAUDE.md` and `migrations/AGENTS.md`:** schema and DAO tests run with `./gradlew :core:data:jvmTest` (no device needed), and the database lives in `commonMain` with per-platform builders.
- [ ] **Step 5: Commit** — `test(data): Room tests on the JVM; DAO repositories to commonMain`
- [ ] **Step 6: PR B gate:** push, open the PR, `ios-compile` green, device smoke test on both flavors (an existing install upgraded in place keeps its items; add and edit an item; log a wear; Journal and Stats show it; backup export works). Wait for merge.

---

### Task 7: DataStore KMP + `SecretStore`; preference repositories to `commonMain`

**Files:**
- Create (commonMain): `repository/SecretStore.kt`, `repository/PreferencesDataStores.kt`; (androidMain) `PreferencesDataStores.android.kt`; (jvmMain/iosMain) `PreferencesDataStores.{jvm,ios}.kt`, `InMemorySecretStore.kt` (one file in `commonMain`, used by the JVM and iOS Koin modules)
- Move (git mv) to `commonMain`: `repository/AiPreferencesRepository.kt`, `WeatherPreferencesRepository.kt`
- Modify: `repository/EncryptedKeyStore.kt` (androidMain, implements `SecretStore`); `di/DataKoinModule.kt` (bind `EncryptedKeyStore` as `SecretStore`); the Koin test overrides that mock `EncryptedKeyStore` (`SingletonParityTest`, `DataKoinModuleTest` if it does, and the chat, recommendations, wardrobe, gms and app graph tests) so they mock `SecretStore` instead; `gradle/libs.versions.toml` / `core/data/build.gradle.kts` (`androidx.datastore:datastore-preferences-core` in `commonMain`, okio)
- Tests: `androidHostTest/.../PreferencesPathTest.kt`, `jvmTest/.../AiPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `interface SecretStore { fun openAiKeyFlow(): Flow<String>; fun anthropicKeyFlow(): Flow<String>; fun geminiKeyFlow(): Flow<String>; suspend fun setOpenAiKey(key: String): Boolean; suspend fun setAnthropicKey(key: String): Boolean; suspend fun setGeminiKey(key: String): Boolean }`. This is exactly `EncryptedKeyStore`'s public surface; add any public member it has that this list misses.
- `class InMemorySecretStore : SecretStore` is the honest stub: keys live only for the process lifetime.
- Produces: `fun createPreferencesDataStore(path: String): DataStore<Preferences>` (common, `PreferenceDataStoreFactory.createWithPath { path.toPath() }`), and per-platform path helpers. Android: `fun androidPreferencesPath(filesDir: File, name: String): String = File(filesDir, "datastore/$name.preferences_pb").absolutePath`, which is the path `preferencesDataStore(name)` uses today.
- `AiPreferencesRepository(aiDataStore: DataStore<Preferences>, secretStore: SecretStore)` and `WeatherPreferencesRepository(weatherDataStore: DataStore<Preferences>)`. The two DataStores are Koin singles qualified `named("ai_prefs")` and `named("weather_prefs")`. Each file must have exactly one DataStore instance per process.
- `AiPreferencesRepository.migrateKeysFromPlainDataStore()` keeps its behavior.

- [ ] **Step 1: Write the failing tests**
  - `PreferencesPathTest.matchesLegacyPreferencesDataStorePath` (androidHostTest): `androidPreferencesPath(File("/data/x/files"), "ai_prefs")` == `File("/data/x/files", "datastore/ai_prefs.preferences_pb").absolutePath`; same for `"weather_prefs"`.
  - `AiPreferencesRepositoryTest` (jvmTest, temp-file DataStore + `InMemorySecretStore`): a value set through the repository (selected provider) reads back from a new repository instance on the same file; `setOpenAiKey("k")` shows up in its key flow.
- [ ] **Step 2: Run them to verify they fail** (compile FAIL).
- [ ] **Step 3: Implement, move the two repositories, and rewire Koin.** `ClosetApp`'s eager `AiPreferencesRepository` resolution (from #55) stays.
- [ ] **Step 4: Verify** — the full verification command → BUILD SUCCESSFUL; the device tests pass.
- [ ] **Step 5: Commit** — `refactor(data): DataStore KMP and SecretStore; preference repositories to commonMain`

---

### Task 8: Ktor engines, weather and model discovery to `commonMain`, `LocationProvider` interface

**Files:**
- Create (commonMain): `di/HttpClients.kt` (moves `buildHttpClient` + `SENSITIVE_HEADERS` out of `DataKoinModule.kt`), `location/LocationProvider.kt` (interface)
- Move (git mv) Android `location/LocationProvider.kt` → `androidMain/.../location/AndroidLocationProvider.kt`
- Move to `commonMain`: `weather/*.kt` (including `WeatherServiceClient.kt`), `repository/WeatherRepository.kt`, `repository/ModelDiscoveryRepository.kt`, `di/AiHttpClient.kt`
- Modify: `di/DataKoinModule.kt` (split, see below), `core/data/build.gradle.kts` (Ktor core/content-negotiation/logging in `commonMain`; OkHttp in `androidMain` and `jvmMain`; `ktor-client-darwin` in `iosMain`)
- Tests: `commonTest/.../di/HeaderRedactionTest.kt`

**Interfaces:**
- Produces: `fun buildHttpClient(engine: HttpClientEngineFactory<*>, json: Json, socketTimeoutMs: Long, requestTimeoutMs: Long, logTag: String, isDebug: Boolean): HttpClient`, with the same plugins, timeouts and log levels as today.
- Produces: `internal fun redactSensitiveHeaders(message: String): String`, extracted from the logger unchanged; `buildHttpClient`'s logger calls it.
- Produces: `interface LocationProvider { suspend fun getLocation(timeoutMs: Long = 10_000L): Pair<Double, Double>? }`. `AndroidLocationProvider(context)` is today's implementation. JVM and iOS bind `object NoLocationProvider : LocationProvider` (returns `null`; the spec's platform gap: weather is hidden when location is unavailable).
- Koin: `commonDataModule` (commonMain: everything platform-neutral) + `androidDataModule` (androidMain: Context-based singles, OkHttp engine, WorkManager, workers, Android `LocationProvider`, `EncryptedKeyStore` as `SecretStore`, the DataStores). On Android, `val dataModule = module { includes(commonDataModule, androidDataModule) }`, so `allModules` and the feature tests are unchanged. JVM and iOS get `jvmDataModule` / `iosDataModule` with the stubs; they aren't started anywhere yet.

- [ ] **Step 1: Write `HeaderRedactionTest` (commonTest)**

```kotlin
@Test fun redactsEverySecretHeaderCaseInsensitively() {
    val msg = "REQUEST\nAuthorization: Bearer sk-123\nx-api-key: abc\nX-Goog-Api-Key: g-1\nAccept: application/json"
    val out = redactSensitiveHeaders(msg)
    assertFalse(out.contains("sk-123")); assertFalse(out.contains("abc")); assertFalse(out.contains("g-1"))
    assertTrue(out.contains("Accept: application/json"))
    assertTrue(out.contains("Authorization: <REDACTED>"))
}
```

- [ ] **Step 2: Run it to verify it fails** (compile FAIL).
- [ ] **Step 3: Implement the split, the engines and the `LocationProvider` interface; move the weather and model-discovery code.**
- [ ] **Step 4: Verify** — the full verification command → BUILD SUCCESSFUL; `AppKoinGraphTest` passes in both flavors; `DataKoinModuleTest`/`SingletonParityTest` still pass (the AI-vs-default client distinctness test included).
- [ ] **Step 5: Commit** — `refactor(data): Ktor clients, weather and model discovery in commonMain; LocationProvider interface`

---

### Task 9: `EmbeddingEncoder` and work schedulers as common interfaces; docs; phase gate

**Files:**
- Create (commonMain): `util/EmbeddingEncoder.kt` (interface), `worker/WorkStatus.kt`; (jvmMain/iosMain or commonMain) `util/UnavailableEmbeddingEncoder.kt`, `worker/NoOpSchedulers.kt`
- Move (git mv) Android `util/EmbeddingEncoder.kt` → `androidMain/.../util/OnnxEmbeddingEncoder.kt`
- Move to `commonMain`: `worker/EmbeddingWork.kt`, `ImageCompressionWork.kt`, `BatchSegmentationWork.kt` (constants + scheduler interfaces); Android scheduler implementations stay in `androidDataModule` / wardrobe
- Modify: `worker/EmbeddingWorker.kt` (androidMain); `features/settings/.../AiSettingsScreen.kt` and its ViewModel (`WorkInfo` → `WorkStatus`); `features/chat/.../ChatRepository.kt` (if it uses `EmbeddingEncoder`'s concrete type); `features/wardrobe/.../di/WardrobeKoinModule.kt` (BatchSegmentationScheduler impl maps to `WorkStatus`); the spec's follow-ups if anything moved scope; `app-android/CLAUDE.md`
- Tests: `androidHostTest/.../worker/WorkStatusMappingTest.kt`, `commonTest/.../util/UnavailableEmbeddingEncoderTest.kt`

**Interfaces:**
- Produces: `interface EmbeddingEncoder { val isAvailable: Boolean; val modelVersion: String; suspend fun encode(text: String): Result<FloatArray> }`. `OnnxEmbeddingEncoder(context)` is today's class, with `modelVersion = EmbeddingWork.MODEL_VERSION` (`"arctic-embed-xs-q8-v1"`). `EmbeddingWorker` uses `encoder.modelVersion` for `getItemIdsNeedingEmbedding` and for the stored `model_version`, so the value written doesn't change. `UnavailableEmbeddingEncoder` has `isAvailable = false`, the same `modelVersion`, and `encode` returns `Result.failure(UnsupportedOperationException("On-device embeddings are not available on this platform"))`.
- Produces: `enum class WorkState { ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED }` and `data class WorkStatus(val id: String, val state: WorkState, val done: Int, val total: Int, val failed: Int, val skipped: Int)`. Android: `fun WorkInfo.toWorkStatus(): WorkStatus` reads the progress (or, for finished work, output) keys `KEY_DONE`/`KEY_TOTAL`/`KEY_FAILED`/`KEY_SKIPPED`, with 0 when absent.
- The scheduler interfaces' `workInfo: Flow<WorkInfo?>` becomes `workStatus: Flow<WorkStatus?>`. Settings reads `WorkStatus` with the same logic: running = `RUNNING || ENQUEUED`, the success snackbar keyed by `id`, the same progress text.
- JVM/iOS: no-op schedulers (`schedule`/`runNow`/`cancel` do nothing, `workStatus = flowOf(null)`, `BatchSegmentationScheduler.isSupported = false`) and `UnavailableEmbeddingEncoder`, bound in `jvmDataModule` / `iosDataModule`.

- [ ] **Step 1: Write the failing tests**
  - `WorkStatusMappingTest` (androidHostTest; build `WorkInfo` via its public constructor): a RUNNING info with progress `done=3,total=10,failed=1` maps to `WorkStatus(id, RUNNING, 3, 10, 1, 0)`; a SUCCEEDED info with output `done=10,total=10,skipped=2` maps to `done=10, skipped=2`; missing keys map to 0.
  - `UnavailableEmbeddingEncoderTest` (commonTest): `isAvailable` is false, `modelVersion == "arctic-embed-xs-q8-v1"`, and `encode("x").isFailure`.
- [ ] **Step 2: Run them to verify they fail** (compile FAIL).
- [ ] **Step 3: Implement the interfaces, the Android implementations and the stubs, then update the settings, chat and wardrobe consumers.**
- [ ] **Step 4: Verify**
  - The full verification command → BUILD SUCCESSFUL.
  - `./gradlew :core:data:connectedAndroidDeviceTest` → passes.
  - Confirm the remaining Android-only parts of `core/data`: `find core/data/src/androidMain -name "*.kt"` lists only the platform implementations, workers, `StorageRepository`, `CaptionEnrichmentProvider`, `BitmapUtils`/`PaletteUtil`/`ColorMatcher`, the Android builders and the Koin module. Paste the list in the report.
- [ ] **Step 5: Update docs**
  - `CLAUDE.md`: the `core/data` source-set layout (what goes in common vs. android), the platform interfaces (`SecretStore`, `LocationProvider`, `EmbeddingEncoder`, schedulers + `WorkStatus`), Kermit for `core/data`, the verification command, and `jvmTest` for Room.
  - The spec's Phase 3 line: note `StorageRepository`/`ImageProcessor` → Phase 5 and the JVM ONNX encoder → follow-up.
- [ ] **Step 6: Commit** — `refactor(data): EmbeddingEncoder and schedulers as common interfaces (KMP Phase 3)`
- [ ] **Step 7: PR C gate:** push, open the PR, `ios-compile` green, device smoke test on both flavors.
  - Settings → AI & Image Tools: the embedding, compression and (full) batch-segmentation cards show the same states and progress as before, and "run now" works.
  - Chat retrieves a newly added item after embedding.
  - Weather still shows when enabled.
