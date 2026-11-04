plugins {
    id("com.android.application") version "9.5.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

val releaseKey = file(System.getProperty("user.home")).resolve(".config/islauncher/release.p12")

android {
    namespace = "io.github.gastoncampana.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gastoncampana.launcher"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (releaseKey.exists()) create("release") {
            storeFile = releaseKey
            storePassword = releaseKey.resolveSibling("password").readText()
            keyAlias = "islauncher"
            keyPassword = storePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the published builds' key, this machine's debug key: scripts/release.sh refuses to ship that.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    // Per-app language picker: locales come from res/values* (the default's from res/resources.properties).
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub in local unit tests.
    testImplementation("org.json:json:20260814")
}
