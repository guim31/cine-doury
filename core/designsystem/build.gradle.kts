plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.cinedoury.android.compose)
}

android {
    namespace = "fr.guim.cinedoury.core.designsystem"
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.tv.material)
    // The components of the phone and the tablet (M1, package `mobile`): R8 leaves them out of the TV's APK.
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    implementation(libs.coil.compose)
    // The headers of a picture the server only gives with a token (trickplay).
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
