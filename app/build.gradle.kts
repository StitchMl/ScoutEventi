plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose")
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
        sourceCompatibility = JavaVersion.VERSION_22
        targetCompatibility = JavaVersion.VERSION_22
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(22)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22)
    }
}

dependencies {
    implementation(libs.androidx.compose.material3.material3)
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose.v1110)
    implementation(libs.androidx.compose.material3.material33)
    implementation(libs.androidx.compose.ui.ui3)
    implementation(libs.androidx.compose.ui.ui.tooling.preview3)
    debugImplementation(libs.androidx.compose.ui.ui.tooling3)

    implementation(libs.androidx.lifecycle.runtime.ktx.v294)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx.v1170)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network + Parsing
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Utils
    implementation(libs.timber)

    // Pull-to-refresh (se lo usi)
    implementation(libs.accompanist.swiperefresh)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v370)
    androidTestImplementation(libs.ui.test.junit4)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose.v1110)
    implementation(libs.androidx.compose.material3.material34)
    implementation(libs.androidx.compose.ui.ui4)
    implementation(libs.androidx.compose.ui.ui.tooling.preview4)
    debugImplementation(libs.androidx.compose.ui.ui.tooling4)

    implementation(libs.androidx.lifecycle.runtime.ktx.v294)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Accompanist SwipeRefresh
    implementation(libs.accompanist.swiperefresh)

    // Custom Tabs
    implementation(libs.androidx.browser)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx.v291)

    // Material icons (required for Icons.Default.*)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore (to store the IDs of events already seen)
    implementation(libs.androidx.datastore.preferences)

    // Notification compat (usually already transitive, but I make it explicit)
    implementation(libs.androidx.core.ktx.v1170)
}