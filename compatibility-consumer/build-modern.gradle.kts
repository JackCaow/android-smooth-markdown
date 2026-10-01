plugins {
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}
android {
    namespace = "consumer.compatibility"
    compileSdk = 35
    defaultConfig { applicationId = "consumer.compatibility"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions.jvmTarget = "17" }
dependencies {
    implementation("io.github.jackcaow:smooth-markdown:" + providers.gradleProperty("artifactVersion").get())
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
}
