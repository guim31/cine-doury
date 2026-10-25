plugins {
    alias(libs.plugins.cinedoury.android.library)
}

android {
    namespace = "fr.guim.cinedoury.core.playback"
}

dependencies {
    api(projects.core.source)
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)

    testImplementation(testFixtures(projects.core.source))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
