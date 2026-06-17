plugins {
    alias(libs.plugins.slskd.android.library)
    alias(libs.plugins.slskd.android.library.compose)
}

android {
    namespace = "com.slskdandroid.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.navigation.suite)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
}
