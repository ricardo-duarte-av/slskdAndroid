plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.browse.impl"
}

dependencies {
    api(projects.feature.browse.api)
}
