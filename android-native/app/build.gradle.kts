plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}
// Production signing material is always provided at build time by the owner.
// Never commit a keystore/password or silently ship a debug-signed release.
val releaseSigningEnv = listOf(
    "GUD_UPLOAD_KEYSTORE_PATH",
    "GUD_UPLOAD_KEYSTORE_PASSWORD",
    "GUD_UPLOAD_KEY_ALIAS",
    "GUD_UPLOAD_KEY_PASSWORD",
)
val releaseSigningConfigured = releaseSigningEnv.all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "in.growurdhan.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "in.growurdhan.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3000
        versionName = "0.3.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("playUpload") {
                storeFile = file(System.getenv("GUD_UPLOAD_KEYSTORE_PATH"))
                storePassword = System.getenv("GUD_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GUD_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("GUD_UPLOAD_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
kotlin { jvmToolchain(17) }
kapt { correctErrorTypes = true }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain"))
    implementation(project(":data"))
    // ARM64-only release module; default x86_64 emulator debug remains lightweight.
    releaseImplementation(project(":casparser-runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }
    .configureEach {
        doFirst {
            check(releaseSigningConfigured) {
                "Release signing credentials are missing; refuse an unsigned/distribution build."
            }
        }
    }

// This evaluation runs before any release output is produced by an invoked task.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.path == ":app:bundleRelease" || it.path == ":app:assembleRelease" }) {
        check(releaseSigningConfigured) {
            "All four GUD_UPLOAD_* signing environment variables are required for release."
        }
        check(file(System.getenv("GUD_UPLOAD_KEYSTORE_PATH")).isFile) {
            "Upload keystore is missing; no release may be produced."
        }
    }
}
