import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // KMP Android libraries only create lint tasks for main code when this plugin is applied.
    alias(libs.plugins.android.lint)
}

kotlin {
    compilerOptions {
        // Room KSP generates `actual object <Db>Constructor`; expect/actual classes are Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.closet.core.data"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // The ONNX model and vocab.txt in src/androidMain/assets are read by EmbeddingEncoder.
        androidResources {
            enable = true
        }

        withHostTestBuilder {}

        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

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
            implementation(libs.kermit)
            // Flow and CancellationException (DataResult, NanoInitializer)
            implementation(libs.kotlinx.coroutines.core)
            // Serialization (ChatResponseParser, cache DTOs, service response parsing)
            implementation(libs.kotlinx.serialization.json)
            // LocalDate and date arithmetic; entity and DAO signatures expose its types.
            api(libs.kotlinx.datetime)
            // Room KMP: entities, DAOs and the database live in commonMain.
            implementation(libs.room.runtime)
            // The bundled SQLite driver every target uses (configureClosetDatabase).
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)

            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)

            // Palette
            implementation(libs.androidx.palette.ktx)

            // DataStore
            implementation(libs.androidx.datastore.preferences)

            // Encrypted storage for API keys
            implementation(libs.androidx.security.crypto)

            // Ktor HTTP client
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // WorkManager (for EmbeddingWorker; Koin worker factory via koin-androidx-workmanager)
            implementation(libs.androidx.work.runtime.ktx)

            // ONNX Runtime (sentence embedding inference)
            implementation(libs.onnx.runtime.android)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test.junit4)
            implementation(libs.mockk)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.espresso.core)
            implementation(libs.room.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// FreshInstallSchemaTest (jvmTest) reads the committed schema export, schemas/…/1.json.
tasks.named<Test>("jvmTest") {
    val schemaDir = layout.projectDirectory.dir("schemas")
    inputs.dir(schemaDir).withPropertyName("roomSchemas").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("closet.schemaDir", schemaDir.asFile.absolutePath)
}

dependencies {
    // Room KSP for every target; each generates its own `actual object ClothingDatabaseConstructor`.
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// KMP creates no `test` task here, so the root `./gradlew test` would skip core/data.
// This lifecycle task runs the Android host tests and the JVM tests.
tasks.register("test") {
    group = "verification"
    description = "Runs core/data's Android host tests and JVM tests."
    dependsOn("testAndroidHostTest", "jvmTest")
}
