plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.toolateforteddy.tvlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toolateforteddy.tvlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // A fixed key checked into the repo so every build (local or CI) can
    // install over the previous one. This app is only ever sideloaded.
    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("keystore/sideload.keystore")
            storePassword = "android"
            keyAlias = "sideload"
            keyPassword = "android"
        }
    }

    // preview: a normal app you open from the apps list; never touches the Home button.
    // home: registers as a home screen, for when it is ready to replace the stock one.
    flavorDimensions += "mode"
    productFlavors {
        create("preview") {
            dimension = "mode"
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "Home (test)")
        }
        create("home") {
            dimension = "mode"
            resValue("string", "app_name", "Home")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // The home build is deliberately not in the apps list.
        disable += "MissingLeanbackLauncher"
    }
    buildFeatures {
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
