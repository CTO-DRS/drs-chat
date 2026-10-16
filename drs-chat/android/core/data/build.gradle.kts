plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "drs.chat.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric tests use JUnit 4 runner; junit-vintage-engine bridges
        // them onto the JUnit Platform configured in the root build. We do
        // NOT call useJUnitPlatform() here because the Robolectric JUnit 5
        // extension is not yet stable on AGP 8.7; JUnit 4 + vintage is the
        // battle-tested path.
    }
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:protocol"))
    api(project(":core:crypto"))

    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    api(libs.sqlcipher.android)
    api(libs.androidx.sqlite)

    compileOnly(libs.libsignal.android)
    testImplementation(libs.libsignal.client)

    testImplementation(libs.robolectric)
    testImplementation(libs.junit.jupiter)
    // Required so the JUnit Platform (root config) can run JUnit 4 + Robolectric.
    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
