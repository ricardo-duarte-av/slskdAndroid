plugins {
    alias(libs.plugins.slskd.android.library)
}

android {
    namespace = "com.slskdandroid.feature.settings.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
