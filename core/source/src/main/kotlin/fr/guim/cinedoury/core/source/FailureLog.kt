package fr.guim.cinedoury.core.source

/**
 * Where every caught failure is written, with its stack: logcat on Android (tag `CineDoury`), so
 * that a failure on a real device can be read with `adb logcat` (`SPEC.md` § 4.12). Nothing here
 * may carry a secret: messages name a request by its method and path only.
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

    /** Silent until the app plugs logcat in (and in JVM tests). */
    @Volatile
    var sink: Sink = Sink { _, _, _ -> }

    /** A failure the app recovers from or shows to the user. */
    fun warn(
        message: String,
        error: Throwable? = null,
    ) = sink.write(Level.Warning, message, error)

    /** A failure that should not happen: a bug, or a broken device (Keystore, database). */
    fun error(
        message: String,
        error: Throwable? = null,
    ) = sink.write(Level.Error, message, error)
}
