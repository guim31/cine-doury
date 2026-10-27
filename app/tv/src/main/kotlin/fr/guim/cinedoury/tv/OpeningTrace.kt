package fr.guim.cinedoury.tv

import android.os.Trace

/**
 * The time a screen takes to open, from the key that asks for it to its first frame, as a section of
 * the system trace (`SPEC.md` § 4.20): `NavigationBenchmarks` reads it (`TraceSectionMetric`), and so
 * does Perfetto on a device. Free when nothing records a trace.
 */
internal object OpeningTrace {
    /** The section of [route], if its opening is measured: a details screen and the settings. */
    fun sectionOf(route: Route): String? =
        when (route) {
            is Route.Details -> OPEN_DETAILS
            Route.Settings -> OPEN_SETTINGS
            else -> null
        }

    /** The entries pushed whose first frame is still to come. */
    private val opening = mutableSetOf<Long>()

    fun begin(entry: BackStackEntry) {
        val section = sectionOf(entry.route) ?: return
        opening += entry.id
        Trace.beginAsyncSection(section, entry.id.toInt())
    }

    fun end(entry: BackStackEntry) {
        val section = sectionOf(entry.route) ?: return
        if (opening.remove(entry.id)) Trace.endAsyncSection(section, entry.id.toInt())
    }

    const val OPEN_DETAILS = "CineDoury.openDetails"
    const val OPEN_SETTINGS = "CineDoury.openSettings"
}
