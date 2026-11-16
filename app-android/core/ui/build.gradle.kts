import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // KMP Android libraries only create lint tasks for main code when this plugin is applied.
    alias(libs.plugins.android.lint)
}

kotlin {
    android {
        namespace = "com.closet.core.ui"
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

            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            // api: feature modules call painterResource/stringResource/getString on Res.* values.
            api(libs.compose.mp.components.resources)

            // Image loading
            implementation(libs.coil.compose)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            // api: PreferencesRepository's constructor takes DataStore<Preferences>.
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
            implementation(libs.ktor.client.okhttp)
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
    publicResClass = true
    packageOfResClass = "com.closet.core.ui.resources"
    generateResClass = always
}

// KMP creates no `test` task here, so the root `./gradlew test` would skip core/ui.
// This lifecycle task runs the Android host tests and the JVM tests.
tasks.register("test") {
    group = "verification"
    description = "Runs core/ui's Android host tests and JVM tests."
    dependsOn("testAndroidHostTest", "jvmTest")
}
