pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("consumerArtifactVersion").isPresent) {
            maven { url = uri(rootDir.resolve("build/central-staging")) }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "android-smooth-markdown"
include(":smoothmarkdown-core", ":smoothmarkdown", ":app", ":consumer-smoke", ":consumer-reader")
