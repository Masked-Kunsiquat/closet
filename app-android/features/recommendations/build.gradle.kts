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
        namespace = "com.closet.features.recommendations"
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
            // Navigation (JetBrains; common toRoute)
            implementation(libs.jetbrains.navigation.compose)

            implementation(project(":core:data"))
            implementation(project(":core:ui"))

            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            // Lifecycle
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            // Image loading
            implementation(libs.coil.compose)

            // Ktor HTTP client (Anthropic / OpenAI-compatible providers)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {

            // @Preview annotation for RecommendationPreviews.kt
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui.tooling.preview)
        }
        jvmTest.dependencies {
            // The scoreboard builds a real ClothingDatabase, so Room types must be on the test classpath.
            implementation(libs.room.runtime)
            implementation(libs.mockk)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
            implementation(libs.ktor.client.okhttp)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test.junit4)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime.ktx)
        }
    }
}

apply(from = rootProject.file("gradle/skiko-jvm-test-runtime.gradle.kts"))

compose.resources {
    packageOfResClass = "com.closet.features.recommendations.resources"
    generateResClass = always
}

// Runtime-only (KMP Android library has no debugImplementation): lets Android Studio render @Preview.
dependencies {
    "androidRuntimeClasspath"(platform(libs.androidx.compose.bom))
    "androidRuntimeClasspath"(libs.androidx.ui.tooling)
}

// The recommendation scoreboard (a simulated year per persona, scored by common-sense checks) is
// slow, so it runs as its own task, `scoreboard`, over jvmTest's compiled classes; jvmTest skips it.
val scoreboardPackage = "com.closet.features.recommendations.scoreboard.*"
val jvmTestTask = tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching(scoreboardPackage) }
}
tasks.register<Test>("scoreboard") {
    group = "verification"
    description = "Runs the recommendation scoreboard."
    testClassesDirs = jvmTestTask.get().testClassesDirs
    classpath = jvmTestTask.get().classpath
    filter { includeTestsMatching(scoreboardPackage) }
    val reportDir = layout.buildDirectory.dir("reports/scoreboard")
    systemProperty("scoreboard.reportDir", reportDir.get().asFile.absolutePath)
    outputs.dir(reportDir)
    testLogging { showStandardStreams = true }
}

// KMP creates no `test` task here, so the root `./gradlew test` would skip features/recommendations.
// This lifecycle task runs the Android host tests and the JVM tests.
tasks.register("test") {
    group = "verification"
    description = "Runs features/recommendations' Android host tests and JVM tests."
    dependsOn("testAndroidHostTest", "jvmTest")
}
