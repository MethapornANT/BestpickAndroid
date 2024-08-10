plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

buildscript {
    repositories {
        google() // Essential for Google dependencies
        mavenCentral() // General repository for other dependencies
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2") // Adjust as needed
        classpath("com.google.gms:google-services:4.4.2") // Ensure compatibility with other versions
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.3")
    }
}


