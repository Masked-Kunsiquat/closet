// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Aggregate iOS compile check for KMP modules (main sources for both iOS targets,
// test sources for the simulator). Wiring uses task paths (strings), so it is lazy
// and independent of project evaluation order.
val iosCompileCheck by tasks.registering {
    group = "verification"
    description = "Compiles every Kotlin Multiplatform module for iOS (arm64 device + simulator)."
}

subprojects {
    val subproject = this
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        iosCompileCheck.configure {
            dependsOn(
                "${subproject.path}:compileKotlinIosArm64",
                "${subproject.path}:compileKotlinIosSimulatorArm64",
                // Test sources too (simulator only: same sources, so one target is enough).
                "${subproject.path}:compileTestKotlinIosSimulatorArm64",
            )
        }
    }
}
