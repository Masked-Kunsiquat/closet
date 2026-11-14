# KMP toolchain spike findings

Spike branch: `spike/kmp-toolchain` (throwaway module `:spike:kmp`, never merged).
Baseline kept unchanged: Kotlin 2.3.20, AGP 9.1.0, Gradle 9.4.0, KSP 2.3.2, Room 2.8.4.
Everything below was verified on 2026-11-14 with targets `androidLibrary`, `jvm()`,
`iosArm64()`, `iosSimulatorArm64()`.

## Versions

| Library / plugin | Coordinates | Version | Notes |
|---|---|---|---|
| Koin (BOM) | `io.insert-koin:koin-bom` (+ `koin-core` without a version) | **4.2.2** | Latest stable. Built against Kotlin 2.3.20 exactly. `4.3.0-Beta1` exists; not used. |
| Compose Multiplatform (libraries) | `org.jetbrains.compose.runtime:runtime`, `org.jetbrains.compose.foundation:foundation` | **1.12.2** | Latest stable (`1.13.0-beta02` exists). |
| JetBrains Compose Gradle plugin | `org.jetbrains.compose` | **1.12.2** | Same version as the CMP libraries. |
| CMP Material 3 | `org.jetbrains.compose.material3:material3` | **1.9.0** | Material 3 is versioned separately from CMP. 1.9.0 is the latest stable, and it's what the CMP 1.12.2 plugin's (deprecated) `compose.material3` accessor resolves to. |
| Room KMP | `androidx.room:room-runtime`, `room-compiler`, plugin `androidx.room` | **2.8.4** | Kept at the app's existing version (2.8.5 is out; no bump needed). |
| Bundled SQLite | `androidx.sqlite:sqlite-bundled` | **2.6.2** | The exact `androidx.sqlite` version that Room 2.8.4 requires. 2.7.1 exists, but don't move it independently of Room. |
| Kermit | `co.touchlab:kermit` | **2.1.0** | 2.2.0 is the latest, but it's built against Kotlin 2.3.21 (stdlib gets pulled above the compiler). 2.1.0 is built against 2.2.0. |
| Coil 3 | `io.coil-kt.coil3:coil-compose` | **3.4.0** (see Surprises) | The last release built against Kotlin 2.3.x (2.3.10). It's built against CMP 1.9.3/Skiko 0.9.22.2, so it raises a Skiko mismatch warning against CMP 1.12.2. |
| JetBrains navigation-compose | `org.jetbrains.androidx.navigation:navigation-compose` | **2.10.0** | Latest stable. Needs CMP runtime ≥ 1.11.0 and lifecycle 2.11.0. |
| KMP lifecycle-viewmodel | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | **2.11.0** | Latest stable. |
| Kotlin Multiplatform plugin | `org.jetbrains.kotlin.multiplatform` | 2.3.20 | Same as `kotlin`. |
| Android KMP library plugin | `com.android.kotlin.multiplatform.library` | 9.1.0 | Same as `agp`. |
| KSP | `com.google.devtools.ksp` | 2.3.2 (unchanged) | 2.3.14 is the latest. 2.3.2 works, so no bump. |

## Plugins

The module applies these, in this order:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)                 // org.jetbrains.kotlin.multiplatform
    alias(libs.plugins.android.kotlin.multiplatform.library) // com.android.kotlin.multiplatform.library
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)                                 // androidx.room
    alias(libs.plugins.compose.multiplatform)                // org.jetbrains.compose
    alias(libs.plugins.kotlin.compose)                       // org.jetbrains.kotlin.plugin.compose
}
```

- All four new plugins must also be declared `apply false` in the root `build.gradle.kts`, next to the existing ones.
- Android target DSL: `kotlin { androidLibrary { namespace = ...; compileSdk = 36; minSdk = 26; withHostTestBuilder {} } }`. AGP 9.1.0 printed no deprecation warning for `androidLibrary`.
- Host (unit) tests on Android only exist if `withHostTestBuilder {}` is declared. The source set is `androidHostTest`, and `commonTest` runs there too.
- Set the JVM target per target with `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` inside both `androidLibrary {}` and `jvm {}`. No toolchain is needed, and the build ran on the Android Studio JBR.
- Room: `room { schemaDirectory("$projectDir/schemas") }`, plus top-level `dependencies { add("kspAndroid", room.compiler); add("kspJvm", ...); add("kspIosArm64", ...); add("kspIosSimulatorArm64", ...) }`. Those configuration names resolve as written. `@Database` needs `@ConstructedBy(XConstructor::class)` with an `expect object XConstructor : RoomDatabaseConstructor<X>`, and Room's KSP generates the `actual` for each target.
- The CMP libraries are declared by Maven coordinates through the catalog, not through the deprecated `compose.*` accessors.

## `gradle.properties` changes

**None.** `android.builtInKotlin=false` and `android.newDsl=false` stay as they are. The KMP library plugin configures, compiles and runs host tests with both of them set. AGP prints `WARNING: The option setting 'android.builtInKotlin=false' is deprecated` (and the same for `newDsl`), but that warning was there before the spike and it isn't an error.

## Task names

| Purpose | Task |
|---|---|
| Android host (unit) test | `:spike:kmp:testAndroidHostTest` (aggregate: `testAndroid`) |
| JVM test | `:spike:kmp:jvmTest` |
| iOS device compile | `:spike:kmp:compileKotlinIosArm64` |
| iOS simulator compile | `:spike:kmp:compileKotlinIosSimulatorArm64` |
| Room KSP per target | `kspAndroidMain`, `kspKotlinJvm`, `kspKotlinIosArm64`, `kspKotlinIosSimulatorArm64` |
| Aggregate iOS check (root) | `iosCompileCheck` (picks up `:spike:kmp` automatically) |

## CI timing

Pending controller push. Step 6 (`iosCompileCheck` on `macos-latest`) hasn't run yet. Record the job's wall-clock time and the result of `:spike:kmp:compileKotlinIosArm64` / `compileKotlinIosSimulatorArm64` here once it does.

## Surprises

1. **Coil 3 vs Kotlin 2.3.20 vs CMP 1.12.** No Coil release is both built with Kotlin 2.3.x and aligned with CMP 1.12:
   - Coil 3.5.0+ is built with Kotlin 2.4.x. 3.6.x (the series aligned with CMP 1.12 / Skiko 0.150.x) is built with 2.4.10, and it pulls `kotlin-stdlib` up to **2.4.10** on every classpath. With 3.6.3, JVM and Android compiled and tested fine on Kotlin 2.3.20 (the compiler reads metadata one version ahead). iOS is untested: Kotlin/Native 2.3.20 reading 2.4.10 klibs is a risk.
   - Coil 3.4.0 (pinned in the spike) is built with Kotlin 2.3.10, but Gradle warns `Skiko dependencies' versions are incompatible: coil-core-jvm:3.4.0 -> skiko 0.9.22.2 -> 0.150.2`. This affects the JVM and iOS targets, which render with Skiko. Android doesn't use Skiko.
   - **Recommendation:** when a later phase moves image loading into common code, bump Kotlin to 2.4.x and use Coil 3.6.x. Until then, keep Coil on the Android side only (the app's existing Coil 2.7.0).
2. **Compose hot-reload adds a `dev` JVM compilation.** The CMP 1.12 plugin pulls in `org.jetbrains.compose.hot-reload` and adds `jvmDev` tasks (for example `kspDevKotlinJvm` and `processJvmDevResources`). They're harmless, but they show up in task lists.
3. **`expect object` Beta warning.** Room's generated `actual object ...Constructor` triggers the `'expect'/'actual' classes are in Beta` warning. Real modules can silence it with `freeCompilerArgs.add("-Xexpect-actual-classes")`.
4. **Windows file lock on `features/stats` `classes.jar`.** A long-lived Gradle daemon kept a handle open on `features/stats/build/.../bundleLibCompileToJarDebug/classes.jar` after a `./gradlew test` run. The next `assembleFullDebug` then failed with `FileSystemException ... being used by another process`. This is environmental, not caused by KMP. `./gradlew --stop` (or a fresh checkout) clears it. Step 5 (`assembleFullDebug assembleFossDebug`) was therefore checked on a clean copy of the tree (with `:spike:kmp` included) using `--no-daemon`: BUILD SUCCESSFUL in 4m 20s, 392 tasks.
5. **The Material 3 version differs from the CMP version.** There's no `material3:1.12.x` stable. Pin it separately (`composeMultiplatformMaterial3`).
