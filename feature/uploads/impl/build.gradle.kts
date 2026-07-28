plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.uploads.impl"
}

dependencies {
    api(projects.feature.uploads.api)
    implementation(projects.core.model)
    // api, not implementation: the ViewModel's constructor exposes @DefaultDispatcher, so
    // the consuming module's Hilt/KSP must be able to resolve it (see CLAUDE.md).
    api(projects.core.common)
}
