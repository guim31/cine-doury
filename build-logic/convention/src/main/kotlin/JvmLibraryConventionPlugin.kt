import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Pure Kotlin module: no Android dependency (`:core:model`, `:core:source`, `:source:*`).
 *
 * Its code still runs on Android, down to `minSdk` (API 30), where Java APIs newer than Android 11
 * are missing (`URLEncoder.encode(String, Charset)`: essai réel 2). Android Lint does not check it
 * (`NewApi` is skipped outside Android modules): Animal Sniffer checks the main classes against the
 * Android API 30 signature, D8 backports included (`animalsnifferMain`, part of `check`).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("ru.vyarus.animalsniffer")
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
                add("signature", "${libs.library("gummy-bears-api30").get()}@signature")
            }
            configureDetekt()
            configureTests()
        }
    }
}
