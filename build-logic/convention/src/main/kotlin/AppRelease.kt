import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The version of an app, the same for the TV and the phone (M1, `SPEC.md` § 4.26): from the tag of the
 * release (`vX.Y.Z`, § 4.18), or of a trial build (`vX.Y.Z-essai.N`, essai réel 4); the release
 * workflow sets CINE_DOURY_VERSION. Without it, a development version, which is offered no update.
 */
fun Project.releaseVersion(): String? =
    providers
        .environmentVariable("CINE_DOURY_VERSION")
        .orNull
        ?.removePrefix("v")
        ?.takeIf { Regex("""\d{1,4}\.\d{1,2}\.\d{1,2}(-essai\.\d{1,2})?""").matches(it) }

/**
 * X × 1 000 000 + Y × 10 000 + Z × 100 + N, N the number of a trial build (0 for a release): grows with
 * the version (AppVersion.versionCode in :core:update).
 */
fun versionCodeOf(version: String): Int {
    val (release, trial) = version.split("-essai.").let { it[0] to (it.getOrNull(1)?.toInt() ?: 0) }
    val (major, minor, patch) = release.split('.').map(String::toInt)
    return major * 1_000_000 + minor * 10_000 + patch * 100 + trial
}

/**
 * The version and the keys of an app: every app of the repository is signed with the same keys, so
 * that the TV and the phone come from the same hand (M1).
 *
 * - The debug key, fixed and versioned (`app/tv/debug.keystore`): every debug APK, from the CI or from
 *   any machine, installs over the previous one. A debug key is not a secret (README.md).
 * - The release key, read from the environment the release workflow makes from the secrets of the
 *   repository (`SPEC.md` § 4.18), never in the repository. Without it (a pull request, a fork, a
 *   machine), the release APK falls back on the debug key.
 */
fun ApplicationExtension.versionAndSigning(project: Project) {
    val version = project.releaseVersion()
    defaultConfig {
        versionCode = version?.let(::versionCodeOf) ?: 1
        versionName = version ?: "0.1.0-dev"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = project.rootProject.file("app/tv/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val keystore = project.providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
        if (keystore != null) {
            create("release") {
                storeFile = project.file(keystore)
                storeType = "pkcs12"
                storePassword = project.providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = project.providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = project.providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            // The release key when the release workflow has it. Without it (the CI of a pull request, a
            // machine), the versioned debug key, so that the optimised APK installs over the debug APK on
            // the emulators of the CI; such an APK is never published.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}
