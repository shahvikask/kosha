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
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}
