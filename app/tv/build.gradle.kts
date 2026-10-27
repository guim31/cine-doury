plugins {
    alias(libs.plugins.cinedoury.android.application)
    alias(libs.plugins.cinedoury.android.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

// The version comes from the tag of the release (`vX.Y.Z`, SPEC.md § 4.18): the release workflow sets
// CINE_DOURY_VERSION. Without it, a development version, which is offered no update.
val releaseVersion: String? =
    providers
        .environmentVariable("CINE_DOURY_VERSION")
        .orNull
        ?.removePrefix("v")
        ?.takeIf { Regex("""\d{1,4}\.\d{1,2}\.\d{1,2}""").matches(it) }

/** X × 10 000 + Y × 100 + Z: grows with the version (AppVersion.versionCode in :core:update). */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split('.').map(String::toInt)
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "fr.guim.cinedoury.tv"

    defaultConfig {
        applicationId = "fr.guim.cinedoury"
        versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
        versionName = releaseVersion ?: "0.1.0-dev"
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
        // The release key, distinct from the debug key: read from the environment the release
        // workflow makes from the secrets of the repository (SPEC.md § 4.18), never in the repository.
        // Without it (a pull request, a fork, a machine), the release APK falls back on the debug key.
        val keystore = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storeType = "pkcs12"
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // The optimised APK (SPEC.md § 4.16): R8 shrinks and optimises the code and the resources, the
        // Baseline and Startup Profiles (src/release/generated/baselineProfiles, made on the emulator
        // of the CI by :benchmark) get it compiled ahead of time, and lay out the dex for the start.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The release key when the release workflow has it (SPEC.md § 4.18). Without it (the CI of a
            // pull request, a machine), the versioned debug key, so that the optimised APK installs over
            // the debug APK on the emulators of the CI; such an APK is never published.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
