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
    }

    buildTypes {
        // The release APK is signed outside the CI by the maintainer (SPEC.md § 4).
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
