plugins {
    alias(libs.plugins.slskd.android.library)
}

android {
    namespace = "com.slskdandroid.feature.search.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
