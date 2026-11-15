# KMP toolchain spike findings

Spike branch: `spike/kmp-toolchain` (throwaway module `:spike:kmp`, never merged).
Targets: `android {}` (KMP Android library), `jvm()`, `iosArm64()` and `iosSimulatorArm64()`.
Verified 2026-11-14 on AGP 9.1.0 and Gradle 9.4.0.

> **Prerequisite: Kotlin 2.3.20 → 2.4.x is required before any module targets iOS.**
> CI round 1 (Kotlin 2.3.20) failed at `:spike:kmp:compileKotlinIosArm64`:
> `KLIB resolver: Skipping '.../navigation-runtime-iosArm64Main-2.10.0.klib' having incompatible ABI version '2.4.0'. The library was produced by '2.4.20' compiler. The current Kotlin compiler can consume libraries having ABI version <= '2.3.0'.`
> Kotlin/Native, unlike Kotlin/JVM, can't read klibs made by a newer compiler.
> The bump also requires **Hilt 2.59.2 → 2.60.1** and **one source fix** in the existing app (see "App-side fallout").

## Versions

| Library / plugin | Coordinates | Version | Notes |
|---|---|---|---|
| **Kotlin** (KGP, compose compiler plugin, serialization plugin) | `org.jetbrains.kotlin.*` | **2.4.21** (was 2.3.20) | Latest stable 2.4 patch. Every iOS klib in the graph is ABI ≤ 2.4.0; the newest producer is Kotlin 2.4.20 (navigation 2.10.0). |
| **KSP** | `com.google.devtools.ksp` | **2.3.14** (was 2.3.2) | Latest. KSP2 versions are decoupled from Kotlin. 2.3.10 added the Kotlin 2.4 module-name fix. Avoid 2.3.13 (concurrent-execution regression). |
| **Hilt** | `com.google.dagger:hilt-*`, plugin `com.google.dagger.hilt.android` | **2.60.1** (was 2.59.2) | Required for Kotlin 2.4: 2.59.2 fails with `Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`. 2.60 errors if the build sets a plugin-controlled flag; this build sets none. |
| Koin (BOM) | `io.insert-koin:koin-bom` (+ `koin-core` without a version) | **4.2.2** | Latest stable, built with Kotlin 2.3.20. (`4.3.0-Beta1` exists.) |
| Compose Multiplatform (libraries) | `org.jetbrains.compose.runtime:runtime`, `org.jetbrains.compose.foundation:foundation` | **1.12.2** | Latest stable. |
| JetBrains Compose Gradle plugin | `org.jetbrains.compose` | **1.12.2** | Same as the CMP libraries. |
| CMP Material 3 | `org.jetbrains.compose.material3:material3` | **1.9.0** | Versioned separately. Latest stable, and what CMP 1.12.2's deprecated `compose.material3` accessor resolves to. |
| Room KMP | `androidx.room:room-runtime`, `room-compiler`, plugin `androidx.room` | **2.8.4** | The app's existing version. Works with Kotlin 2.4.21 and KSP 2.3.14. (2.8.5 exists.) |
| Bundled SQLite | `androidx.sqlite:sqlite-bundled` | **2.6.2** | The `androidx.sqlite` version Room 2.8.4 requires. Move it in step with Room. |
| Kermit | `co.touchlab:kermit` | **2.2.0** | Latest. Built with Kotlin 2.3.21. |
| Coil 3 | `io.coil-kt.coil3:coil-compose` | **3.6.3** | Latest, built with Kotlin 2.4.10. Aligned with CMP 1.12 / Skiko 0.150.x, so there's no Skiko mismatch warning. (3.4.0 gave a Skiko mismatch.) |
| JetBrains navigation-compose | `org.jetbrains.androidx.navigation:navigation-compose` | **2.10.0** | Latest stable. Built with Kotlin 2.4.20, which is why 2.4.x is needed. |
| KMP lifecycle-viewmodel | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | **2.11.0** | Latest stable. |
| Kotlin Multiplatform plugin | `org.jetbrains.kotlin.multiplatform` | 2.4.21 | Same as `kotlin`. |
| Android KMP library plugin | `com.android.kotlin.multiplatform.library` | 9.1.0 | Same as `agp`. |

Checking klib ABI versions locally: resolve `iosArm64CompileKlibraries` (an init script with `incoming.artifactView{}.files`) and read `abi_version` / `compiler_version` from each klib's `manifest`. With the versions above, the 72 klibs are ABI 1.8.0, 1.201.0, 2.2.0, 2.3.0 or 2.4.0, and none is newer than 2.4.0. Do this check before any library bump. Windows can't compile iOS, but it can catch this failure.

## Plugins

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

- Declare all four new plugins `apply false` in the root `build.gradle.kts`.
- Android target DSL: `kotlin { android { namespace = ...; compileSdk = 36; minSdk = 26; withHostTestBuilder {} } }`. On Kotlin 2.4, `androidLibrary {}` is **deprecated** ("Please use 'android' instead"). On 2.3.20 it wasn't.
- Android host tests exist only when `withHostTestBuilder {}` is present. The source set is `androidHostTest`, and `commonTest` runs there.
- Set the JVM target per target: `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` in both `android {}` and `jvm {}`. No toolchain is needed.
- Room: `room { schemaDirectory("$projectDir/schemas") }`, plus `dependencies { add("kspAndroid", room.compiler); add("kspJvm", ...); add("kspIosArm64", ...); add("kspIosSimulatorArm64", ...) }`.
  - `@Database` needs `@ConstructedBy(XConstructor::class)` with an `expect object XConstructor : RoomDatabaseConstructor<X>`. KSP generates the `actual` for each target.
  - Add `kotlin { compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") } }` to silence the Beta warning that object triggers.
- Declare the CMP libraries by catalog coordinates, not through the deprecated `compose.*` accessors.

## `gradle.properties` changes

**None.** `android.builtInKotlin=false` and `android.newDsl=false` work with the KMP library plugin on Kotlin 2.3.20 and on 2.4.21. AGP prints its existing deprecation warnings for both flags. Those aren't errors.

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

- **Round 1** (run 43643663230, Kotlin 2.3.20): failed at `:spike:kmp:compileKotlinIosArm64` on the klib ABI error quoted at the top.
- **Round 2** (run 43645675955, Kotlin 2.4.21): **passed**. `iosCompileCheck` compiled `:spike:kmp` for `iosArm64` and `iosSimulatorArm64` on `macos-latest`. The whole job took about 4m 55s on a cold Kotlin/Native cache. Expect each additional KMP module to add compile time.

## App-side fallout of Kotlin 2.3.20 → 2.4.21

Measured with `assembleFullDebug assembleFossDebug` (BUILD SUCCESSFUL, 392 tasks, 4m 57s) and `./gradlew test --continue` after the fixes below.

1. **A warning became an error (KTLC-365).** `core/data/.../OutfitRepository.kt` checked `if (e is AppError)` inside `catch (e: Exception)`. `AppError` extends `Throwable`, not `Exception`, so the check is always false. On 2.3.20 this was a warning ("will become an error in language version 2.4"); on 2.4 it's `e: Check for instance is always 'false'`.
   - It also hid a latent bug: a thrown `AppError.DatabaseError.NotFound` escaped the `catch` instead of becoming `DataResult.Error`.
   - Fix: a separate `catch (e: AppError) { DataResult.Error(e) }` clause before `catch (e: Exception)`. This is the only `is AppError`-in-`catch (Exception)` site.
2. **Hilt.** Hilt 2.59.2 fails `hiltJavaCompile*` on Kotlin 2.4 metadata. Fix: 2.60.1.
3. **KSP / Room.** KSP 2.3.14 and Room 2.8.4 work unchanged on Kotlin 2.4.21, in the app and in the spike.
4. **Annotation use-site default (KEEP 402).** The roughly 20 warnings like `This annotation is currently applied to the value parameter only, but in the future it will also be applied to field/property` are gone, because 2.4 makes that default real. Annotations on constructor `val` parameters (Hilt qualifiers such as `@ApplicationContext`, and serialization annotations in the AI providers) now also land on the field or property. No build or test broke, but watch reflection-based code.
5. **Unit tests.** All 193 runnable unit tests pass (`core:data`, `features:chat` and `features:recommendations`, both flavors). The only failures are the 3 known-broken test compiles from the baseline: `features:stats` `StatsViewModelTest` and `features:wardrobe` `ClothingFormViewModelTest` (both flavors), which are stale against current APIs. None are new.

## Surprises

1. **Kotlin/Native klib compatibility drives the Kotlin version.** A single library built with a newer Kotlin (navigation 2.10.0, built with 2.4.20) blocks iOS compilation outright. On JVM and Android the same graph compiled and tested fine on 2.3.20, so only the macOS CI job caught it. Use the local klib-manifest check above before bumping anything.
2. **Coil 3 vs Kotlin.** Coil 3.5.0+ is built with Kotlin 2.4.x, and 3.6.x pulls `kotlin-stdlib` 2.4.10. On Kotlin 2.4.21 that's fine. On 2.3.20 the only Kotlin-compatible Coil was 3.4.0, which has a Skiko mismatch against CMP 1.12.
3. **Compose hot-reload adds a `dev` JVM compilation** (`jvmDev`, `kspDevKotlinJvm` and so on). It's harmless.
4. **Windows file lock.** A long-lived Gradle daemon kept `features/stats/.../classes.jar` open after a `test` run, so the next assemble failed with `FileSystemException ... being used by another process`. Stopping that daemon clears it.
5. **Material 3 isn't versioned with CMP.** There's no `material3:1.12.x` stable. Pin it separately (`composeMultiplatformMaterial3`).
