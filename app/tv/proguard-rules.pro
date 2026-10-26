# R8 rules of the optimised APK (SPEC.md § 4.16). The libraries bring their own rules (kotlinx.serialization,
# Room, Media3, Coil, OkHttp, Koin, the Jellyfin SDK); these are what the app adds.

# Shrink and optimise, but keep the names: a stack trace in logcat (`adb logcat -s CineDoury`) and the
# diagnostic report stay readable without a mapping file, and the names cost no speed.
-dontobfuscate
