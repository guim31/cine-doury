plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.cinedoury.android.compose)
}

android {
    namespace = "fr.guim.cinedoury.feature.player"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.playback)
    implementation(libs.media3.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(projects.core.source))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
