// :core-domain — czysty silnik diety (Kotlin/JVM). ZERO zależności od Androida.
// Tu żyje logika przeniesiona z GymTrackera: TDEE/makro, guardraile, estymaty, sloty posiłków.
// Testy ruszają na zwykłym JVM (szybko, bez SDK/emulatora).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
