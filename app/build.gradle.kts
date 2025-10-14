plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "it.buonacaccia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.buonacaccia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.androidx.workmanager)

    // UI - Compose
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.activity.compose.v1110)
    implementation(libs.androidx.compose.ui.ui3)
    implementation(libs.androidx.compose.ui.ui.tooling.preview3)
    debugImplementation(libs.androidx.compose.ui.ui.tooling3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx.v294)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.androidx.compose)

    // Network + Parsing
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Utils
    implementation(libs.timber)
    implementation(libs.androidx.browser)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Pull-to-refresh
    implementation(libs.accompanist.swiperefresh)

    // Core-ktx (already included by other dependencies, but good to have)
    implementation(libs.androidx.core.ktx.v1170)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v370)
    androidTestImplementation(libs.ui.test.junit4)
}