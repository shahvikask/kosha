plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    maxParallelForks = 1
    forkEvery = 1
    maxHeapSize = "1g"
}

dependencies {
    // Authoritative native integer FIFO must validate legacy quantities before
    // SQLCipher projection; no separate/guessing backup reconciliation engine.
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}
