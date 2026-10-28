plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.pill.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pill.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "2.3"
    }

    // CI signs with one permanent key (from repository secrets, see build.yml) so each new
    // APK installs over the previous one. Local builds keep the usual debug key.
    val keystore = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("ci") {
                storeFile = keystore
                // Trimmed: a pasted secret can pick up a trailing newline
                val password = System.getenv("SIGNING_PASSWORD")?.trim()
                storePassword = password
                keyAlias = "pill"
                keyPassword = password
            }
        }
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Screenshot tests (Robolectric + Roborazzi) that render the README images.
    // Run by .github/workflows/screenshots.yml; see Screenshots.kt.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("screenshots.out", rootProject.file("docs/screenshots").path)
                it.systemProperty("screenshots.photos", rootProject.file("build/screenshot-photos").path)
                it.testLogging {
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showStandardStreams = true
                }
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.coil-kt:coil-test:2.7.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
