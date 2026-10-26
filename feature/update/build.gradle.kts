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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.datastore.preferences)
}
