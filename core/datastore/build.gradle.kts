plugins {
    alias(libs.plugins.slskd.android.library)
    alias(libs.plugins.slskd.hilt)
}

android {
    namespace = "com.slskdandroid.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    // api: ConnectionSettingsDataSource's @Inject constructor exposes DataStore<Preferences>,
    // so consumers (and their annotation processors) must be able to resolve the type.
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
