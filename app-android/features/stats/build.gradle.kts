import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // KMP Android libraries only create lint tasks for main code when this plugin is applied.
    alias(libs.plugins.android.lint)
}

kotlin {
    android {
        namespace = "com.closet.features.stats"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // Compose Multiplatform resources are packaged as Android assets only when this is on.
        androidResources {
            enable = true
        }

        withHostTestBuilder {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:ui"))

            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)

            // Image loading
            implementation(libs.coil.compose)

            // Charts
            implementation(libs.vico.compose.m3)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            // Lifecycle
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)

            // Navigation
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)

            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui.tooling.preview)
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
            // Compose resources' getString() touches Skiko on the JVM; tests need its native runtime.
            implementation(compose.desktop.currentOs)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test.junit4)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime.ktx)
        }
    }
}

compose.resources {
    packageOfResClass = "com.closet.features.stats.resources"
    generateResClass = always
}

// KMP creates no `test` task here, so the root `./gradlew test` would skip features/stats.
// This lifecycle task runs the Android host tests and the JVM tests.
tasks.register("test") {
    group = "verification"
    description = "Runs features/stats' Android host tests and JVM tests."
    dependsOn("testAndroidHostTest", "jvmTest")
}
