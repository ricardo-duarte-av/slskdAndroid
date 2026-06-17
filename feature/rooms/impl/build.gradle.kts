plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.rooms.impl"
}

dependencies {
    api(projects.feature.rooms.api)
}
