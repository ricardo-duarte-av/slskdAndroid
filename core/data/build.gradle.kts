plugins {
    alias(libs.plugins.slskd.android.library)
    alias(libs.plugins.slskd.hilt)
}

android {
    namespace = "com.slskdandroid.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.core.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
