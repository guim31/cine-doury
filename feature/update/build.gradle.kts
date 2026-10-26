plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.cinedoury.android.compose)
}

android {
    namespace = "fr.guim.cinedoury.feature.update"
}

dependencies {
    api(projects.core.update)
    implementation(projects.core.ui)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
