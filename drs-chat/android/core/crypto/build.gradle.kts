plugins {
    alias(libs.plugins.kotlin.jvm)
}
// :core:crypto — pure JVM. compileOnly libsignal-client so the main source
// compiles against the real Rust API; tests run against libsignal-client
// (desktop natives, real engine). The Android :app provides libsignal-android
// at runtime (same org.signal.libsignal.protocol.* API, Android natives).
dependencies {
    api(project(":core:domain"))
    api(project(":core:protocol"))
    compileOnly(libs.libsignal.client)
    api(libs.bouncycastle.prov)
    testImplementation(libs.libsignal.client)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}
