plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.downloads.impl"
}

dependencies {
    api(projects.feature.downloads.api)
    implementation(projects.core.model)
}
