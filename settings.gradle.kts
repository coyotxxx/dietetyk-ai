pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dietetyk-ai"

// :core-domain — czysty silnik diety (Kotlin/JVM, bez Androida).
// :data — Room + mappery do modeli rdzenia + SyncApi (furtka WWW) + OpenFoodFacts.
// :app — Compose UI + workery + self-updater.
// :ai dojdzie w kolejnym inkremencie (klient Claude + tool-use).
include(":core-domain")
include(":data")
include(":ai")
include(":app")
