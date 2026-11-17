import org.gradle.api.artifacts.VersionCatalogsExtension

// Adds the Skiko native runtime for the machine running the JVM tests (Windows, Linux CI, macOS).
// Compose resources' getString() touches Skiko on the JVM. Apply after the `kotlin { }` block of
// any module with a jvmTest source set.
val skikoOs = System.getProperty("os.name").lowercase()
val skikoArch = System.getProperty("os.arch").lowercase()
val skikoIsX64 = skikoArch == "amd64" || skikoArch == "x86_64"
val skikoIsArm = skikoArch == "aarch64" || skikoArch == "arm64"
val skikoAlias = when {
    "mac" in skikoOs && skikoIsArm -> "skiko-awt-runtime-macos-arm64"
    "mac" in skikoOs && skikoIsX64 -> "skiko-awt-runtime-macos-x64"
    "windows" in skikoOs && skikoIsX64 -> "skiko-awt-runtime-windows-x64"
    "linux" in skikoOs && skikoIsX64 -> "skiko-awt-runtime-linux-x64"
    else -> error("Unsupported host for Skiko test runtime: $skikoOs/$skikoArch")
}

val skikoRuntime = extensions.getByType<VersionCatalogsExtension>().named("libs")
    .findLibrary(skikoAlias).get()

dependencies {
    add("jvmTestImplementation", skikoRuntime)
}
