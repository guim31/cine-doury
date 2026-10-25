import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose on an Android module; apply after the library or application convention plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            when (val android = extensions.getByName("android")) {
                is ApplicationExtension -> android.buildFeatures.compose = true
                is LibraryExtension -> android.buildFeatures.compose = true
                else -> error("cinedoury.android.compose needs an Android application or library module")
            }
            dependencies {
                val bom = platform(libs.library("compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.library("compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("compose-ui-tooling"))
            }
        }
    }
}
