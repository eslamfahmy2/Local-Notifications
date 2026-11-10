import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test doubles shared by the JVM tests (src/test) and device tests (src/androidTest).
// Only ever added as testImplementation / androidTestImplementation.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:domain"))
}
