plugins {
    alias(libs.plugins.slskd.android.library)
    alias(libs.plugins.slskd.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.slskdandroid.core.network"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.signalr)
}
