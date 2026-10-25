plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.cinedoury.android.compose)
}

android {
    namespace = "fr.guim.cinedoury.feature.settings"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.feature.manage)

    testImplementation(testFixtures(projects.core.source))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
