// Canonical settings — CI + dev machines with the Android SDK.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "drs-chat-android"
include(":app", ":core:domain", ":core:protocol", ":core:crypto", ":core:data",
        ":transport:api", ":transport:websocket", ":feature:chat",
        ":testing:signal-sim", ":testing:e2e")
include(":relay"); project(":relay").projectDir = file("../server/relay")
