package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.TranscodeReason
import java.time.Instant

/**
 * The last decisions of playback (J7, `SPEC.md` § 4.19): what was played, how (direct, remux,
 * transcoding) and why, for the diagnostic report. Kept in memory, the last [CAPACITY]; never an
 * address nor a token.
 */
object PlaybackLog {
    /**
     * @param fallback the player could not read the file as it was: the server transcodes it now.
     * @param forced the file played as it is although the server would not have sent it so.
     */
    data class Entry(
        val at: Instant,
        val title: String,
        val method: PlayMethod,
        val reasons: Set<TranscodeReason>,
        val container: String?,
        val video: String?,
        val audio: String?,
        val bitrate: Int?,
        val fallback: Boolean = false,
        val forced: Boolean = false,
    )

    const val CAPACITY = 20

    private val entries = ArrayDeque<Entry>()

    fun record(entry: Entry) =
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > CAPACITY) entries.removeFirst()
        }

    /** The decisions kept, the oldest first. */
    fun recent(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}
