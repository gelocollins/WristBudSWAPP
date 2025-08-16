plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "angelo.collins.wristbud"
    compileSdk = 36

    defaultConfig {
        applicationId = "angelo.collins.wristbud"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    implementation ("androidx.wear.compose:compose-material:1.2.1")
    implementation ("androidx.wear.tiles:tiles-material:1.1.0")
    implementation ("androidx.wear:wear-tooling-preview:1.0.0")
    implementation ("androidx.activity:activity-compose:1.8.1")
    implementation ("androidx.compose.material3:material3:1.2.1")
    implementation ("com.google.android.horologist:horologist-compose-layout:0.5.6") // check latest version
    // add the ohttp3
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation ("androidx.health.connect:connect-client:1.1.0-rc03")
    implementation(files("libs/samsung-health-sensor-api-v1.4.0.aar"))
}