// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath ("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    // Plugins con versiones específicas
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false

}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
