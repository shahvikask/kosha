plugins {
    alias(libs.plugins.android.library)
    id("com.chaquo.python")
}

android {
    namespace = "in.growurdhan.casruntime"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        // pypdfium2 5.13.0 currently ships an Android ARM64 wheel, but no
        // matching Android x86_64 wheel in its published release assets.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPython("python3.13")
        pip {
            install("pypdfium2==5.13.0")
            install("python-dateutil==2.9.0.post0")
        }
    }
    sourceSets.getByName("main") {
        // Reference frozen source without copying or modifying it. The existing
        // black-box Python entrypoint's imports remain identical to legacy.
        srcDir("../../scripts")
        srcDir("../../src/casparser-main")
        srcDir("../app/src/main/python")
    }
}
