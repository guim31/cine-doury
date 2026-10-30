plugins {
    alias(libs.plugins.cinedoury.android.application)
    alias(libs.plugins.cinedoury.android.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "fr.guim.cinedoury.tv"

    defaultConfig {
        applicationId = "fr.guim.cinedoury"
        // On the emulator of the CI: real playback (PlaybackOnDeviceTest) and the sign-in with the remote
        // (SignInJourneyTest) against the test Jellyfin, the real Keystore (KeystoreTokenCipherTest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The version from the tag, the debug and release keys: shared with :app:mobile (build-logic,
    // AppRelease.kt).
    versionAndSigning(project)

    buildTypes {
        // The optimised APK (SPEC.md § 4.16): R8 shrinks and optimises the code and the resources, the
        // Baseline and Startup Profiles (src/release/generated/baselineProfiles, made on the emulator
        // of the CI by :benchmark) get it compiled ahead of time, and lay out the dex for the start.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

baselineProfile {
    // Generated on the emulator of the CI and versioned (`[record-profiles]`, CLAUDE.md): a build never
    // starts an emulator.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    // The Startup Profile lays out the classes of the start first in the dex (R8).
    dexLayoutOptimization = true
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
    implementation(projects.feature.update)
    implementation(projects.source.jellyfin)
    implementation(projects.source.seerr)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Applies the Baseline Profile of the APK when it is installed by adb rather than by a store.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)

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
