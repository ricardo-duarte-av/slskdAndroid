import java.util.Properties

plugins {
    alias(libs.plugins.slskd.android.application)
    alias(libs.plugins.slskd.android.application.compose)
    alias(libs.plugins.slskd.hilt)
}

// Release signing — read from keystore.properties (local, gitignored) when present,
// otherwise fall back to environment variables (used by CI). When neither is available
// (e.g. a contributor without the keystore), the release config is left unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.slskdandroid"

    defaultConfig {
        applicationId = "pt.aguiarvieira.androidslskd"
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
