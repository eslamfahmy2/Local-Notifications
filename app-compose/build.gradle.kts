import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.localnotifications.compose"
    compileSdk = 36

    defaultConfig {
        // Same id as the shipping app plus a suffix, so both can be installed side by side.
        applicationId = "com.example.localnotifications.compose"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "BASE_URL", "\"http://lynxapp.com/\"")

        // Starts device tests with Hilt's test application (see :core:device-testing).
        testInstrumentationRunner = "com.example.localnotifications.core.devicetesting.HiltTestRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric Compose tests need the merged resources and manifest.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Showcase only: this module is debug-only, so its release variant is disabled.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":feature:list"))
    implementation(project(":feature:detail"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)

    // Compose UI tests on the JVM (Robolectric). Screens are tested with plain state;
    // routes run in the real Activities with Hilt swapping data and scheduling for fakes.
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    // Device tests (src/androidTest): only what needs a real device, such as real rotation and the
    // system permission dialog. Run with ./gradlew :app-compose:connectedDebugAndroidTest
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:device-testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    kspAndroidTest(libs.hilt.compiler)

    // Declares the empty host activity createComposeRule() launches. Debug only, like this module.
    debugImplementation(libs.compose.ui.test.manifest)
}
