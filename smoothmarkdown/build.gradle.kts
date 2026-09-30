import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
    signing
}

android {
    namespace = "com.jackcaow.smoothmarkdown"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
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
    providers.environmentVariable("VERSION").orElse("0.1.0").get()
} else {
    providers.gradleProperty("publicationVersion").orElse("0.1.0").get()
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
            url = uri(layout.buildDirectory.dir("central-staging"))
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

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.erweixin:ratex-android:0.1.14")
    implementation("io.coil-kt:coil-svg:2.7.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
