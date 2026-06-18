plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.rooms.impl"
}

dependencies {
    api(projects.feature.rooms.api)
    implementation(projects.core.model)
    // Extended icon set (BOM-managed) for room glyphs not in material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)
}
