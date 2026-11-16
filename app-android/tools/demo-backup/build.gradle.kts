import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Dev-only command-line tool: builds a demo-wardrobe `.hangr` backup from a local copy of the
// Kaggle "Fashion Product Images (Small)" dataset. It is not part of the app and ships nowhere.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.closet.tools.demobackup.MainKt")
}

dependencies {
    // The real Room database, DAOs and repositories (resolves core/data's jvm() target).
    implementation(project(":core:data"))
    // core/data keeps these as `implementation`, so the tool declares what it calls directly.
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    // BackupWriterTest pins the manifest field names against the app's BackupManifest source.
    systemProperty(
        "closet.backupManifestSource",
        rootProject.file("app/src/main/kotlin/com/closet/backup/BackupManifest.kt").absolutePath,
    )
}
