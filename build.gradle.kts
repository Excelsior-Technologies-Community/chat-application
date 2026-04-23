
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false

    id("com.google.dagger.hilt.android") version "2.57.2" apply false
}