plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// Source builds produce the binaries shipped in the AAR. Maven consumers need no Rust/NDK.
val rustInputs = fileTree("rust-core") { exclude("target/**", ".git/**") }
val rustOutput = layout.projectDirectory.dir("smoothmarkdown/build/generated/rust")
val buildAndroidRustParser = tasks.register<Exec>("buildAndroidRustParser") {
    group = "build"
    description = "Build the owned Rust parser JNI library for four Android ABIs with 16KB ELF alignment"
    inputs.files(rustInputs, file("scripts/build-rust-parser.py"), file("smoothmarkdown-core/src/main/cpp/smooth_markdown_rust_jni.c"))
    outputs.dir(rustOutput.dir("jniLibs"))
    commandLine("python3", "scripts/build-rust-parser.py", "--android")
}
val buildHostRustParser = tasks.register<Exec>("buildHostRustParser") {
    group = "verification"
    description = "Build desktop JNI for tests that require the real Rust parser"
    inputs.files(rustInputs, file("scripts/build-rust-parser.py"), file("smoothmarkdown-core/src/main/cpp/smooth_markdown_rust_jni.c"))
    outputs.dir(rustOutput.dir("host"))
    commandLine("python3", "scripts/build-rust-parser.py", "--host", "--java-home", System.getProperty("java.home"))
}
