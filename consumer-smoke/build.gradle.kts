plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { val artifactVersion = providers.gradleProperty("consumerArtifactVersion").orNull
    implementation(if (artifactVersion == null) project(":smoothmarkdown-core") else "io.github.jackcaow:smoothmarkdown-core:$artifactVersion") }
tasks.register<JavaExec>("verifyConsumer") {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("consumer.CoreConsumerKt")
}
