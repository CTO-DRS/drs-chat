plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
// :relay is a PURE JVM Ktor server (per PLAN §4.3 + ADR-0002 byte-pipe rule).
// NEVER parses envelope payloads — verifies only the auth-challenge Ed25519
// signature (so it knows the sender) and routes opaque bytes by the 16-byte
// recipientKeyId. Per ADR-0006 (planned): server never shares code with the
// Android build; this single build is enough.
dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
