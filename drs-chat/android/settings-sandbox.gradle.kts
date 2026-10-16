// Sandbox settings — pure-JVM subset (no Android SDK needed).
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
rootProject.name = "drs-chat-android-sandbox"
include(":core:domain", ":core:protocol", ":core:crypto", ":transport:api", ":transport:websocket",
        ":testing:signal-sim", ":testing:e2e")
include(":relay"); project(":relay").projectDir = file("../server/relay")
