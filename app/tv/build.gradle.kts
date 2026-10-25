plugins {
    alias(libs.plugins.cinedoury.android.application)
    alias(libs.plugins.cinedoury.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "fr.guim.cinedoury.tv"

    defaultConfig {
        applicationId = "fr.guim.cinedoury"
        versionCode = 1
        versionName = "0.1.0"
        // On the emulator of the CI: real playback (PlaybackOnDeviceTest) and the sign-in with the remote
        // (SignInJourneyTest) against the test Jellyfin, the real Keystore (KeystoreTokenCipherTest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed debug key, versioned (app/tv/debug.keystore): every debug APK, from the CI or from
        // any machine, installs over the previous one. A debug key is not a secret (README.md).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // The release APK is signed outside the repository by the maintainer (SPEC.md § 4).
        release {
            isMinifyEnabled = false
        }
    }
}

roborazzi {
    // Reference screenshots, versioned. `recordRoborazziDebug` writes them, `verifyRoborazziDebug`
    // compares against them.
    outputDir.set(file("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    // Real shadows in Robolectric screenshots.
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.details)
    implementation(projects.feature.home)
    implementation(projects.feature.library)
    implementation(projects.feature.manage)
    implementation(projects.feature.player)
    implementation(projects.feature.search)
    implementation(projects.core.playback)
    implementation(projects.feature.settings)
    implementation(projects.source.jellyfin)
    implementation(projects.source.seerr)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(projects.core.source))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(testFixtures(projects.core.source))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
