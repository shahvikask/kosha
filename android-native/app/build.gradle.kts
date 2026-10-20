plugins {
    id("com.chaquo.python")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "in.growurdhan.redevelopment"
    compileSdk = 35
    defaultConfig {
        applicationId = "in.growurdhan.redevelopment"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-m00"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxParallelForks = 1
            it.maxHeapSize = "2g"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}


/**
 * The sources below are the frozen original Python parser and its original
 * Android/desktop entrypoints, packaged without source modification.
 * Missing Android Python wheels MUST fail the build, not silently fall back
 * to a fabricated statement or a remote service.
 */
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("pydantic>=2.3,<3")
            install("pypdfium2>=5,<7")
            install("python-dateutil>=2.8.2,<3")
            install("click>=8,<10")
            install("colorama>=0.4.6,<1")
            install("rich>=13.5.2,<16")
        }
    }
    sourceSets {
        getByName("main") {
            srcDir("../../src/casparser-main")
            srcDir("../../scripts")
        }
    }
}
