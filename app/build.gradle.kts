plugins {
    alias(libs.plugins.slskd.android.application)
    alias(libs.plugins.slskd.android.application.compose)
    alias(libs.plugins.slskd.hilt)
}

android {
    namespace = "com.slskdandroid"

    defaultConfig {
        applicationId = "pt.aguiarvieira.androidslskd"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.feature.search.impl)
    implementation(projects.feature.connection.impl)
    implementation(projects.feature.downloads.impl)
    implementation(projects.feature.uploads.impl)
    implementation(projects.feature.rooms.impl)
    implementation(projects.feature.chat.impl)
    implementation(projects.feature.users.impl)
    implementation(projects.feature.browse.impl)

    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
