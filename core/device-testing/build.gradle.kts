import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared setup for device tests (src/androidTest) in both apps: the Hilt test runner and
// UI Automator helpers. Only ever added as androidTestImplementation.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.localnotifications.core.devicetesting"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Their types appear in this module's public helpers, and every device test needs them.
    api(libs.androidx.test.runner)
    api(libs.androidx.test.core)
    api(libs.uiautomator)
    api(libs.hilt.android.testing)
    implementation(libs.androidx.core.ktx)
}
