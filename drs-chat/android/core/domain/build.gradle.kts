plugins { alias(libs.plugins.kotlin.jvm) }
dependencies {
    api(project(":core:protocol"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":core:crypto"))
    testImplementation(libs.libsignal.client) // Required by ReceivePipelineTest for real libsignal sessions
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
}
