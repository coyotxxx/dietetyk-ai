// :ai — klient Claude API + tool-use. Czysty Kotlin/JVM (OkHttp działa na Androidzie i JVM) →
// logika składania requestu/parsowania/pętli tool-use testowalna bez Androida.
// Handlery narzędzi (dostęp do :data) implementuje :app — :ai definiuje tylko interfejs `ToolHandler`.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
