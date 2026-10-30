plugins {
    alias(libs.plugins.cinedoury.android.application)
    alias(libs.plugins.cinedoury.android.compose)
    alias(libs.plugins.roborazzi)
}

// The app for phones and tablets (M1, SPEC.md § 4.26): its own package, so that it lives beside the TV's
// on a device and each updates its own; the same version, the same keys (build-logic, AppRelease.kt).
// The view models, the repositories, the source and the player are the TV's: only the screens differ.
android {
    namespace = "fr.guim.cinedoury.mobile"

    defaultConfig {
        applicationId = "fr.guim.cinedoury.mobile"
        // On the phone emulator of the full CI: a real journey against the test Jellyfin (PhoneJourneyTest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    versionAndSigning(project)

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

roborazzi {
    // Reference screenshots of the phone and the tablet, versioned (docs/screenshots/m1/, from 200).
    outputDir.set(file("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    // Real shadows in Robolectric screenshots.
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.playback)
    implementation(projects.feature.auth)
    implementation(projects.feature.details)
    implementation(projects.feature.home)
    implementation(projects.feature.library)
    implementation(projects.feature.player)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.manage)
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
    testImplementation(libs.kotlin.test)
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
