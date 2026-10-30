package fr.guim.cinedoury.core.update

/**
 * The app that reads the releases (M1, `SPEC.md` § 4.26): each takes its own APK by a stable name,
 * never the first `.apk` of the release, so that a TV never installs the app for phones.
 *
 * - [Tv]: `cine-doury-X.Y.Z.apk`, the name of every release since the 0.1 (the TVs in 1.0.0 take the
 *   first `.apk` of the list, `ReleaseCompatibilityTest`), or `cine-doury-tv-X.Y.Z.apk`;
 * - [Mobile]: `cine-doury-mobile-X.Y.Z.apk`.
 */
enum class AppTarget {
    Tv,
    Mobile,
    ;

    /** The names this app installs, for [version]. */
    fun apkNames(version: AppVersion): Set<String> =
        when (this) {
            Tv -> setOf("$PREFIX-$version.apk", "$PREFIX-tv-$version.apk")
            Mobile -> setOf("$PREFIX-mobile-$version.apk")
        }

    /** The APK of this app among the [assets] of the release of [version]; null when it has none. */
    fun apkOf(
        assets: List<ReleaseAsset>,
        version: AppVersion,
    ): ReleaseAsset? {
        val names = apkNames(version)
        return assets.firstOrNull { it.name in names }
    }

    private companion object {
        const val PREFIX = "cine-doury"
    }
}
