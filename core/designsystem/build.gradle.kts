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
    api(libs.compose.material.icons.core)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
