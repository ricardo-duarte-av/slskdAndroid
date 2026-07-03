plugins {
    alias(libs.plugins.slskd.android.feature)
}

android {
    namespace = "com.slskdandroid.feature.settings.impl"
}

dependencies {
    api(projects.feature.settings.api)
    implementation(projects.core.model)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
