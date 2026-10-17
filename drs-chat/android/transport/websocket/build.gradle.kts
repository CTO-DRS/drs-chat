plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
// :transport:websocket is PURE JVM (Ktor client OkHttp engine). Implements the
// Transport + TransportManager interfaces from :transport:api. Per ADR-0002,
// transports are byte pipes — no envelope parsing, no crypto. The only
// "smart" behaviour here is reconnect with exponential backoff (1s → 60s,
// jitter) per PLAN §5.1.
dependencies {
    api(project(":core:protocol"))
    api(project(":core:domain"))
    api(project(":transport:api"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
