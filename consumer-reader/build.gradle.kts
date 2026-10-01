plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "consumer.smoothmarkdown"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies { val artifactVersion = providers.gradleProperty("consumerArtifactVersion").orNull
    implementation(if (artifactVersion == null) project(":smoothmarkdown") else "io.github.jackcaow:smooth-markdown:$artifactVersion") }
