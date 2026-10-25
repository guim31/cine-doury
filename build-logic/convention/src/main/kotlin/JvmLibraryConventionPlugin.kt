import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin module: no Android dependency (`:core:model`, `:core:source`, `:source:jellyfin`). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JVM_TARGET))
            }
            dependencies {
                add("testImplementation", libs.library("kotlin-test"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
            configureDetekt()
            configureTests()
        }
    }
}
