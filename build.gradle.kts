plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}