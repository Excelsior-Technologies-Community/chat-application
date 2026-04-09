plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)

    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.aarav.chatapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aarav.chatapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }


    kotlinOptions {
        jvmTarget = "17"
    }


    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.0"
    }

}



kapt {
    correctErrorTypes = true
}

dependencies {

    //WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.6")

    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
// Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.compose.foundation)
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("androidx.compose.material3:material3:1.5.0-alpha13")
    implementation("com.posthog:posthog-android:3.0.0")

    // Add the dependency for the Firebase Authentication library
    implementation("com.google.firebase:firebase-auth")

    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}