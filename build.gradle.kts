plugins {
    id("com.android.application") version "9.5.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

android {
    namespace = "io.github.gastoncampana.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gastoncampana.launcher"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Personal sideloaded app: signed with this machine's own (randomly generated) debug key.
            signingConfig = signingConfigs.getByName("debug")
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
