plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
// :core:crypto is PURE JVM — compileOnly libsignal-client (same org.signal.libsignal.protocol.*
// API as libsignal-android). Host-JVM tests run the real Rust engine via testImplementation.
// The Android :app provides libsignal-android (Android natives) at runtime.
dependencies {
    api(project(":core:domain"))
    api(project(":core:protocol"))
    api(libs.bouncycastle.prov)
    compileOnly(libs.libsignal.client)
    testImplementation(libs.libsignal.client)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}
