plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.bouncycastle.prov)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}
