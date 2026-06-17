plugins {
    alias(libs.plugins.slskd.android.library)
}

android {
    namespace = "com.slskdandroid.feature.browse.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
