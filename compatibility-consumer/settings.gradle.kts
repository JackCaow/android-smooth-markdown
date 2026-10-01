pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(providers.gradleProperty("artifactRepository").get()) }
        google(); mavenCentral()
    }
}
rootProject.name = "smoothmarkdown-compatibility-consumer"
rootProject.buildFileName = if (providers.gradleProperty("modernKotlin").isPresent) "build-modern.gradle.kts" else "build.gradle.kts"
