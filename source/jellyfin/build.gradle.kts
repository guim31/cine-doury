plugins {
    alias(libs.plugins.cinedoury.jvm.library)
}

dependencies {
    api(projects.core.source)
    // The SDK stays an implementation detail: none of its types leaves this module.
    implementation(libs.jellyfin.core)
    implementation(libs.jellyfin.api.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // The SDK logs through kotlin-logging, which needs the SLF4J API at runtime; without a binding
    // SLF4J stays silent.
    runtimeOnly(libs.slf4j.api)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(testFixtures(projects.core.source))
}

// Integration tests against a real Jellyfin 12.3 (ci/jellyfin). They need JELLYFIN_URL and are not
// part of `check`: run `./gradlew :source:jellyfin:integrationTest` once the server is up.
testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnit(libs.versions.junit)
            dependencies {
                implementation(project())
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                    outputs.upToDateWhen { false }
                }
            }
        }
    }
}
