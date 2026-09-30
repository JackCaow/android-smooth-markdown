plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { implementation(project(":smoothmarkdown-core")) }
tasks.register<JavaExec>("verifyConsumer") {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("consumer.CoreConsumerKt")
}
