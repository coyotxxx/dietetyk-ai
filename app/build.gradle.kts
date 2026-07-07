import java.util.Properties

// :app — aplikacja Android (Compose UI). Konsumuje :core-domain (silnik) + :data (Room).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.filebit.dietetyk"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.filebit.dietetyk"
        minSdk = 29
        targetSdk = 35
        versionCode = 69
        versionName = "1.6.0"

        // Klucz Claude z local.properties (poza gitem) → BuildConfig. Pusty w czystym buildzie.
        val props = Properties().apply {
            val lp = rootProject.file("local.properties")
            if (lp.exists()) lp.inputStream().use { load(it) }
        }
        buildConfigField("String", "CLAUDE_API_KEY", "\"${props.getProperty("claudeApiKey", "")}\"")
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":data"))
    implementation(project(":ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.code.scanner)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
