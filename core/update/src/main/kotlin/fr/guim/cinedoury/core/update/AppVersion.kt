package fr.guim.cinedoury.core.update

/**
 * A version of the app (`SPEC.md` § 4.17): `X.Y.Z`, from the tag `vX.Y.Z` of its release. Its
 * [versionCode], what Android compares, grows with it: X × 10 000 + Y × 100 + Z (Y and Z below 100).
 * A build without a tag is a development version: [parse] gives null, and nothing is offered to it.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    val versionCode: Int get() = major * MAJOR + minor * MINOR + patch

    val name: String get() = "$major.$minor.$patch"

    override fun compareTo(other: AppVersion): Int = versionCode.compareTo(other.versionCode)

    override fun toString(): String = name

    companion object {
        private const val MAJOR = 10_000
        private const val MINOR = 100
        private val FORMAT = Regex("""v?(\d{1,4})\.(\d{1,2})\.(\d{1,2})""")

        /** « v1.2.3 » or « 1.2.3 »; null for anything else (« dev », « 1.2.3-rc1 »). */
        fun parse(text: String?): AppVersion? {
            val match = text?.trim()?.let { FORMAT.matchEntire(it) } ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
