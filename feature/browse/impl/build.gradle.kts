plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.browse.impl"
}

dependencies {
    api(projects.feature.browse.api)
    implementation(projects.core.model)
    // Extended icon set (BOM-managed) for the Download glyph not present in material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)
}
