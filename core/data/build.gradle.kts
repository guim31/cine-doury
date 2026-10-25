plugins {
    alias(libs.plugins.cinedoury.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "fr.guim.cinedoury.core.data"
}

// The schema of each database version is versioned in schemas/ (CI checks it is up to date): the
// automatic migrations are generated from it, and the migration tests read it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.source)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)

    testImplementation(testFixtures(projects.core.source))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
