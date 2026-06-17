plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.search.impl"
}

dependencies {
    api(projects.feature.search.api)
    implementation(projects.core.model)
    // Extended icon set (BOM-managed) for the Download glyph not present in material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
