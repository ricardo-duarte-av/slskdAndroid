plugins {
    alias(libs.plugins.slskd.android.library)
}

android {
    namespace = "com.slskdandroid.feature.users.api"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
}
