import java.util.Properties

import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.play.PlayPublisherExtension

plugins {
    alias(libs.plugins.slskd.android.application)
    alias(libs.plugins.slskd.android.application.compose)
    alias(libs.plugins.slskd.hilt)
    // Gradle Play Publisher: kept on the classpath but not applied by default, so normal
    // debug/release builds never depend on it. The CI "publish" job opts in by passing
    // -Pslskd.enablePlayPublishing (see the conditional block at the bottom of this file).
    alias(libs.plugins.play.publisher) apply false
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
        versionCode = 2
        versionName = "0.1.1"
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

// Gradle Play Publisher — opt-in. Applied only when the CI publish job passes
// -Pslskd.enablePlayPublishing so day-to-day builds stay independent of it.
// Credentials come from the PLAY_SERVICE_ACCOUNT_JSON env var (a file path to the
// service-account JSON). Track/status are overridable via env for flexibility.
if (providers.gradleProperty("slskd.enablePlayPublishing").isPresent) {
    apply(plugin = "com.github.triplet.play")
    configure<PlayPublisherExtension> {
        System.getenv("PLAY_SERVICE_ACCOUNT_JSON")?.let { serviceAccountCredentials.set(file(it)) }
        defaultToAppBundles.set(true)
        track.set(System.getenv("PLAY_TRACK") ?: "internal")
        releaseStatus.set(
            when (System.getenv("PLAY_RELEASE_STATUS")?.lowercase()) {
                "completed" -> ReleaseStatus.COMPLETED
                "inprogress" -> ReleaseStatus.IN_PROGRESS
                "halted" -> ReleaseStatus.HALTED
                else -> ReleaseStatus.DRAFT
            },
        )
    }
}
