plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.chat.impl"
}

dependencies {
    api(projects.feature.chat.api)
    implementation(projects.core.model)
    // Extended icon set (BOM-managed) for chat glyphs not in material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)
}
