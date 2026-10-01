plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "consumer.smoothmarkdown"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies { val artifactVersion = providers.gradleProperty("consumerArtifactVersion").orNull
    implementation(if (artifactVersion == null) project(":smoothmarkdown") else "io.github.jackcaow:smooth-markdown:$artifactVersion") }
