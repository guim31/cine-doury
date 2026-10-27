package fr.guim.cinedoury.core.source

/**
 * The moments `ci/couch-gestures.sh` times on a real device (`docs/GESTES.md`): a key received, a
 * screen drawn, the picture playing. Silent unless the app plugs logcat in, which it does only after
 * `adb shell setprop log.tag.CineDoury DEBUG`: nothing is written in normal use.
 */
object GestureLog {
    fun interface Sink {
        fun mark(event: String)
    }

    @Volatile
    var sink: Sink = Sink { }

    fun mark(event: String) = sink.mark(event)
}
