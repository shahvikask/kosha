plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Explicit Ed25519 provider; Android default may select AndroidKeyStore.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    testImplementation(libs.junit)
}
