plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.closet.platform.gms"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// GMS / ML Kit implementations of the feature capability interfaces. Only app's full
// flavor depends on this module ("fullImplementation"), so foss builds stay GMS-free.
dependencies {
    implementation(project(":core:data"))
    implementation(project(":features:chat"))
    implementation(project(":features:recommendations"))
    implementation(project(":features:wardrobe"))

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // MLKit GenAI Prompt API — Gemini Nano on-device inference (chat + recommendations)
    implementation(libs.mlkit.genai.prompt)
    // MLKit Entity Extraction — chat date parsing; model downloaded via Play Services (~5.6 MB)
    implementation(libs.mlkit.entity.extraction)
    // MLKit Language Identification — chat router English gate
    implementation(libs.mlkit.language.id)
    // ML Kit Subject Segmentation — wardrobe background removal
    implementation(libs.mlkit.subject.segmentation)
    // ML Kit GenAI Image Description — wardrobe photo captions
    implementation(libs.mlkit.genai.image.description)
    // kotlinx coroutines Task.await() extension (depends on play-services-tasks)
    implementation(libs.kotlinx.coroutines.play.services)

    // Unit tests
    testImplementation(project(":core:ui"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.ktor.client.okhttp)
    testImplementation(libs.androidx.lifecycle.viewmodel.compose)
}
