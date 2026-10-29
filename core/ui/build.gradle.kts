plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.cinedoury.android.compose)
}

android {
    namespace = "fr.guim.cinedoury.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.data)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.koin.androidx.compose)
    implementation(libs.androidx.activity.compose)
}
