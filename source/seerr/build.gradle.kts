plugins {
    alias(libs.plugins.cinedoury.jvm.library)
}

dependencies {
    api(projects.core.source)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
