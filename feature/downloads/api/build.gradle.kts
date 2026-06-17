plugins {
    alias(libs.plugins.slskd.android.library)
}

android {
    namespace = "com.slskdandroid.feature.downloads.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
