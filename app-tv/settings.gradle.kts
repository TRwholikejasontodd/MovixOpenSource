pluginManagement {
    repositories {
        google()
        // Les runners GitHub partagent leurs IP : Maven Central peut répondre 403/429.
        // Son miroir Google passe d'abord ; Maven Central reste disponible en secours.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Les runners GitHub partagent leurs IP : Maven Central peut répondre 403/429.
        // Son miroir Google passe d'abord ; Maven Central reste disponible en secours.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}

rootProject.name = "movix-tv"
include(":app")
include(":extractor")
include(":ffmpeg")
