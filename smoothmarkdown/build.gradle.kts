import org.gradle.api.tasks.testing.Test
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    signing
}

android {
    namespace = "com.jackcaow.smoothmarkdown"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        aarMetadata { minCompileSdk = 35; minAgpVersion = "8.6.0" }
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    // The Compose 1.6 test manifest uses a legacy ActionBar Activity: target 34 avoids
    // both the old-app dialog and API 35 enforced edge-to-edge hiding its test content.
    // This is only the test APK; the Demo targets API 35 and AAR consumers choose their own target.
    testOptions { targetSdk = 34 }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val isJitPack = providers.environmentVariable("JITPACK").orNull == "true"
val publicationVersion = if (isJitPack) {
    providers.environmentVariable("VERSION").orElse("0.5.0").get()
} else {
    providers.gradleProperty("publicationVersion").orElse("0.5.0").get()
}
val publicationGroup = if (isJitPack) {
    providers.environmentVariable("GROUP").orElse("com.github.JackCaow.android-smooth-markdown").get()
} else {
    providers.gradleProperty("publicationGroup").orElse("io.github.jackcaow").get()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    // The public API is Kotlin. Central accepts a documentation JAR without Java-generated Javadoc.
    from(rootProject.file("README.md"))
    from(rootProject.file("docs/reference.md"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = publicationGroup
            artifactId = if (isJitPack) "smoothmarkdown" else "smooth-markdown"
            version = publicationVersion
            afterEvaluate { from(components["release"]) }
            artifact(javadocJar)
            pom {
                name.set("Smooth Markdown for Android")
                description.set("Native Jetpack Compose Markdown reader, stream renderer, and editor")
                url.set("https://github.com/JackCaow/android-smooth-markdown")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("JackCaow")
                        name.set("JackCaow")
                        url.set("https://github.com/JackCaow")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/JackCaow/android-smooth-markdown.git")
                    developerConnection.set("scm:git:ssh://git@github.com/JackCaow/android-smooth-markdown.git")
                    url.set("https://github.com/JackCaow/android-smooth-markdown")
                }
            }
        }
    }
    repositories {
        maven {
            name = "CentralBundle"
            url = uri(rootProject.layout.buildDirectory.dir("central-staging"))
        }
    }
}

val signingKey = providers.environmentVariable("SIGNING_KEY")
    .orElse(providers.gradleProperty("signingKey"))
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
    .orElse(providers.gradleProperty("signingPassword"))
if (signingKey.isPresent && signingPassword.isPresent) {
    signing {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        sign(publishing.publications["release"])
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.moduleName = "smoothmarkdown"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all-compatibility"
    // Core publishes one unversioned source-build JAR; these are compiler inputs, not runtime dependencies.
    val core = rootProject.layout.projectDirectory.dir("smoothmarkdown-core/build")
    kotlinOptions.freeCompilerArgs += "-Xfriend-paths=${core.dir("classes/kotlin/main").asFile},${core.file("libs/smoothmarkdown-core.jar").asFile}"
}

dependencies {
    api(project(":smoothmarkdown-core"))
    api("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Build from owned sources before the Android plugin merges native libraries.
tasks.named("preBuild") { dependsOn(rootProject.tasks.named("buildAndroidRustParser")) }
tasks.withType<Test>().configureEach {
    dependsOn(rootProject.tasks.named("buildHostRustParser"))
    val nativeName = if (System.getProperty("os.name").startsWith("Mac")) "libsmooth_markdown_rust_jni.dylib" else "libsmooth_markdown_rust_jni.so"
    systemProperty("smoothmarkdown.rust.library", rootProject.file("smoothmarkdown/build/generated/rust/host/$nativeName").absolutePath)
    systemProperty("smoothmarkdown.rust.required", "true")
    doFirst {
        // Gradle's isolated worker loader is not necessarily a URLClassLoader.
        systemProperty("smoothmarkdown.test.classpath", classpath.asPath)
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(rootProject.tasks.named("buildAndroidRustParser")) {
            objects.directoryProperty().apply { set(layout.buildDirectory.dir("generated/rust/jniLibs")) }
        }
    }
}
