// :data — warstwa danych (Room + mappery do :core-domain + SyncApi + OpenFoodFacts).
// Android library: Room wymaga Androida; encje NIGDY nie wychodzą poza tę warstwę (mapper na granicy).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pl.filebit.dietetyk.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Eksport schematu Room → testy migracji (żelazna zasada: zero utraty danych).
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    // api → :app widzi typy rdzenia i Room wystawiane przez repozytoria/bazę.
    api(project(":core-domain"))

    implementation(libs.androidx.core.ktx)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
