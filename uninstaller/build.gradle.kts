plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A separate, tiny app that removes Pill Launcher, for when the launcher itself can't be
// reached (it won't open, or the TV keeps returning to it). Published next to launcher.apk.
android {
    namespace = "app.pill.uninstaller"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pill.uninstaller"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // The same permanent key as the launcher when CI has it (see app/build.gradle.kts)
    val keystore = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("ci") {
                storeFile = keystore
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
