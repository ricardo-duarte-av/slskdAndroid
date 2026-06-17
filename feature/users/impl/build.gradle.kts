plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.users.impl"
}

dependencies {
    api(projects.feature.users.api)
}
