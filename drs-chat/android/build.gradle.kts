// Root build — ktlint + detekt + JVM conventions for all subprojects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true; config.from(rootProject.file("config/detekt.yml")); parallel = true
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions { jvmTarget = "17"; freeCompilerArgs = freeCompilerArgs + listOf("-Xjsr305=strict") }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed"); showStandardStreams = false }
    }
}
