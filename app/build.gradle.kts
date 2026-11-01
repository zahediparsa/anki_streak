plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.ankistreak"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ankistreak"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // One fixed key, so every new build installs as an update and your streak is kept.
    signingConfigs {
        create("shared") {
            storeFile = file("streak.keystore")
            storePassword = "ankistreak"
            keyAlias = "streak"
            keyPassword = "ankistreak"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
