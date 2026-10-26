plugins {
    `kotlin-dsl`
}

group = "fr.guim.cinedoury.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.animalsniffer.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "cinedoury.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "cinedoury.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "cinedoury.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidTest") {
            id = "cinedoury.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidCompose") {
            id = "cinedoury.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
    }
}
