package fr.guim.cinedoury.core.source

import java.time.Instant

/**
 * Where every caught failure is written, with its stack: logcat on Android (tag `CineDoury`), so
 * that a failure on a real device can be read with `adb logcat` (`SPEC.md` § 4.13). The last
 * [CAPACITY] stay in memory, for « Journaux de l'appli » and the diagnostic report (J7, `SPEC.md`
 * § 4.19). Nothing here may carry a secret: messages name a request by its method and path only.
 */
object FailureLog {
    enum class Level { Warning, Error }

    fun interface Sink {
        fun write(
            level: Level,
            message: String,
            error: Throwable?,
        )
    }

    /** One failure as the app keeps it: the message, and the class and message of the error. */
    data class Entry(
        val at: Instant,
        val level: Level,
        val message: String,
        val error: String?,
    )

    const val CAPACITY = 50

    /** Silent until the app plugs logcat in (and in JVM tests). */
    @Volatile
    var sink: Sink = Sink { _, _, _ -> }

    private val entries = ArrayDeque<Entry>()

    /** The failures kept, the oldest first. */
    fun recent(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** A failure the app recovers from or shows to the user. */
    fun warn(
        message: String,
        error: Throwable? = null,
    ) = record(Level.Warning, message, error)

    /** A failure that should not happen: a bug, or a broken device (Keystore, database). */
    fun error(
        message: String,
        error: Throwable? = null,
    ) = record(Level.Error, message, error)

    private fun record(
        level: Level,
        message: String,
        error: Throwable?,
    ) {
        val entry =
            Entry(
                at = Instant.now(),
                level = level,
                message = message,
                error = error?.let { "${it::class.simpleName}: ${it.message.orEmpty()}" },
            )
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > CAPACITY) entries.removeFirst()
        }
        sink.write(level, message, error)
    }
}
