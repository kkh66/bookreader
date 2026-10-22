buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 has built-in Kotlin (KGP 2.2.10 by default); bumped to 2.4.20 to match Readium 3.4.0
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}
