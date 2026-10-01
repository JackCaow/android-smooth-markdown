import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
    signing
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
java { withSourcesJar(); withJavadocJar() }
val isJitPack = providers.environmentVariable("JITPACK").orNull == "true"
publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
            groupId = if (isJitPack) providers.environmentVariable("GROUP").orElse("com.github.JackCaow.android-smooth-markdown").get()
                else providers.gradleProperty("publicationGroup").orElse("io.github.jackcaow").get()
            artifactId = "smoothmarkdown-core"
            version = if (isJitPack) providers.environmentVariable("VERSION").orElse("0.3.0").get()
                else providers.gradleProperty("publicationVersion").orElse("0.3.0").get()
            pom {
                name.set("Smooth Markdown Core")
                description.set("Pure JVM source-preserving CommonMark and GFM parser")
                url.set("https://github.com/JackCaow/android-smooth-markdown")
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { id.set("JackCaow"); name.set("JackCaow") } }
                scm {
                    connection.set("scm:git:git://github.com/JackCaow/android-smooth-markdown.git")
                    developerConnection.set("scm:git:ssh://git@github.com/JackCaow/android-smooth-markdown.git")
                    url.set("https://github.com/JackCaow/android-smooth-markdown")
                }
            }
        }
    }
    repositories { maven { name = "CentralBundle"; url = uri(rootProject.layout.buildDirectory.dir("central-staging")) } }
}
val signingKey = providers.environmentVariable("SIGNING_KEY").orElse(providers.gradleProperty("signingKey"))
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orElse(providers.gradleProperty("signingPassword"))
if (signingKey.isPresent && signingPassword.isPresent) {
    signing { useInMemoryPgpKeys(signingKey.get(), signingPassword.get()); sign(publishing.publications["release"]) }
}

dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<Test>().configureEach {
    dependsOn(rootProject.tasks.named("buildHostRustParser"))
    val nativeName = if (System.getProperty("os.name").startsWith("Mac")) "libsmooth_markdown_rust_jni.dylib" else "libsmooth_markdown_rust_jni.so"
    systemProperty("smoothmarkdown.rust.library", rootProject.file("smoothmarkdown/build/generated/rust/host/$nativeName").absolutePath)
    systemProperty("smoothmarkdown.rust.required", "true")
}
tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("rust-core")) { exclude("target/**", ".git/**"); into("rust-core") }
    from("src/main/cpp") { into("native") }
    from(rootProject.file("scripts/build-rust-parser.py")) { into("build-tools") }
    from(rootProject.file("LICENSE"))
}
