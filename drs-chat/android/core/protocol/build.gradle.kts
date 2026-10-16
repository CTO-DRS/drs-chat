plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
// :core:protocol is SELF-CONTAINED — no project deps. Owns the wire format
// (WireEnvelope, EnvelopeCodec, InnerPayload, EnvelopeConstants, EnvelopeKind,
// ContactBundle, Fingerprint, SafetyNumber, WordList) PLUS the shared routing
// token (KeyId/ContactId) and the cross-cutting crypto port (CryptoEnginePort).
// The :core:domain and :core:crypto modules depend on :core:protocol; the
// reverse dep was removed at M3 to break the prior cycle.
dependencies {
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.bouncycastle.prov)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}
