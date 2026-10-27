plugins {
    alias(libs.plugins.cinedoury.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "fr.guim.cinedoury.benchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The emulator of the CI: its timings compare a change with the one before, on the same
        // emulator, never with a real device (SPEC.md § 4.16).
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    // The app under test is driven from this separate APK, as a user would with the remote: its own
    // process, UiAutomator, never the classes of the app (which R8 shrinks in the optimised APK).
    targetProjectPath = ":app:tv"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // The emulator the CI starts (job « Profiles and benchmarks »), with the test Jellyfin.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
