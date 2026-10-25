plugins {
    alias(libs.plugins.cinedoury.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
