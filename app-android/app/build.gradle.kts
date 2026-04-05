import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signing credentials — resolved in priority order:
//   1. Environment variables (set by `op run --env-file=.env.build` locally,
//      or by GitHub Actions secrets in CI)
//   2. keystore.properties fallback (gitignored local file)
// If neither is present the release build type has no signingConfig (unsigned).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingProp(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propKey)?.takeIf { it.isNotBlank() }

val signingStoreFile   = signingProp("KEYSTORE_FILE_PATH", "storeFile")
val signingStorePass   = signingProp("KEYSTORE_PASSWORD",  "storePassword")
val signingKeyAlias    = signingProp("KEY_ALIAS",          "keyAlias")
val signingKeyPass     = signingProp("KEY_PASSWORD",       "keyPassword")
val canSign            = listOf(signingStoreFile, signingStorePass, signingKeyAlias, signingKeyPass).all { it != null }

android {
    namespace = "com.closet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.closet"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 9
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Full build: includes GMS-backed features (Gemini Nano, future segmentation, etc.)
        // Use this for local dev, GitHub releases, and sideload APKs.
        create("full") {
            dimension = "distribution"
        }
        // FOSS build: no Google Play Services dependencies.
        // GMS features are stubbed to no-ops; everything else is identical.
        // Target: F-Droid distribution.
        create("foss") {
            dimension = "distribution"
        }
    }

    if (canSign) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePass
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSign) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    lint {
        // androidx.startup.InitializationProvider is a transitive WorkManager dependency;
        // lint can't resolve transitive classes in release mode — known false positive.
        disable += "MissingClass"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Rename release APKs to hangr-<flavor>-<versionName>.apk
    // e.g. hangr-full-0.3.0.apk, hangr-foss-0.3.0.apk
    applicationVariants.configureEach {
        if (buildType.name == "release") {
            val flavor = flavorName
            val version = versionName
            outputs.configureEach {
                val apkOutput = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                apkOutput?.outputFileName = "hangr-$flavor-$version.apk"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.room.runtime)
    implementation(project(":features:wardrobe"))
    implementation(project(":features:outfits"))
    implementation(project(":features:stats"))
    implementation(project(":features:settings"))
    implementation(project(":features:recommendations"))
    implementation(project(":features:chat"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    
    // Required for Material 3 XML theme parent
    implementation(libs.google.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Logging
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
