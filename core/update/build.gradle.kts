plugins {
    alias(libs.plugins.cinedoury.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
