plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.users.impl"
}

dependencies {
    api(projects.feature.users.api)
    implementation(projects.core.model)
    // Extended icon set (BOM-managed) for profile glyphs not in material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)
}
