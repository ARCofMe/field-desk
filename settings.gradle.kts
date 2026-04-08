pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.android.application") version "8.5.1"
        id("org.jetbrains.kotlin.android") version "1.9.23"
        id("com.chaquo.python") version "15.0.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FieldDesk"
include(":app")
