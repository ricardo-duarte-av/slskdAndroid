plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.uploads.impl"
}

dependencies {
    api(projects.feature.uploads.api)
    implementation(projects.core.model)
}
