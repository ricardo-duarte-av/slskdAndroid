plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.chat.impl"
}

dependencies {
    api(projects.feature.chat.api)
}
