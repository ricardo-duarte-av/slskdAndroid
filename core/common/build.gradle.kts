plugins {
    alias(libs.plugins.slskd.android.library)
    alias(libs.plugins.slskd.hilt)
}

android {
    namespace = "com.slskdandroid.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
