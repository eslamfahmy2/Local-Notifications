import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.localnotifications"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.localnotifications"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "BASE_URL", "\"http://lynxapp.com/\"")

        // Starts device tests with Hilt's test application (see :core:device-testing).
        testInstrumentationRunner = "com.example.localnotifications.core.devicetesting.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Demo app: release uses the debug key so the APK can still be installed.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric UI tests need the merged resources and manifest.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    // Needed only to bind implementations to domain interfaces (the composition root).
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.material)
    implementation(libs.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    // UI and integration tests on the JVM (Robolectric + Espresso), with Hilt swapping in fakes.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.espresso.core)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)

    // Device tests (src/androidTest) for what Robolectric cannot prove: real rotation, the system
    // permission dialog, notifications in the shade. Run with ./gradlew :app-views:connectedDebugAndroidTest
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:device-testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
}
