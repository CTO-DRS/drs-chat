plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
}
