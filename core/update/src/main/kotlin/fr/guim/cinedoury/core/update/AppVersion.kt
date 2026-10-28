package fr.guim.cinedoury.core.update

/**
 * A version of the app (`SPEC.md` § 4.18): `X.Y.Z`, from the tag `vX.Y.Z` of its release, or
 * `X.Y.Z-essai.N` for a trial build (essai réel 4): the code of a branch, signed with the release key
 * so that it installs over the release, never published. Its [versionCode], what Android compares,
 * grows with it: X × 1 000 000 + Y × 10 000 + Z × 100 + N (Y and Z below 100, N from 1 to 99 for a
 * trial, 0 for a release). A trial of 0.9.0 installs over the 0.9.0, and the 0.9.1 over the trial.
 * A build without a tag is a development version: [parse] gives null, and nothing is offered to it.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val trial: Int = 0,
) : Comparable<AppVersion> {
    val versionCode: Int get() = major * MAJOR + minor * MINOR + patch * PATCH + trial

    /** A trial build: never offered as an update (only releases are). */
    val isTrial: Boolean get() = trial > 0

    val name: String get() = "$major.$minor.$patch" + if (isTrial) "$TRIAL$trial" else ""

    override fun compareTo(other: AppVersion): Int = versionCode.compareTo(other.versionCode)

    override fun toString(): String = name

    companion object {
        private const val MAJOR = 1_000_000
        private const val MINOR = 10_000
        private const val PATCH = 100
        private const val TRIAL = "-essai."
        private const val MAX_TRIAL = 99
        private val FORMAT = Regex("""v?(\d{1,4})\.(\d{1,2})\.(\d{1,2})(?:-essai\.(\d{1,2}))?""")

        /** « v1.2.3 », « 1.2.3 » or « 1.2.3-essai.4 »; null for anything else (« dev », « 1.2.3-rc1 »). */
        fun parse(text: String?): AppVersion? {
            val match = text?.trim()?.let { FORMAT.matchEntire(it) } ?: return null
            val (major, minor, patch, trial) = match.destructured
            val number = trial.toIntOrNull() ?: 0
            if (trial.isNotEmpty() && number !in 1..MAX_TRIAL) return null
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt(), number)
        }
    }
}
