package fr.guim.cinedoury.core.model

import java.time.Instant
import kotlin.time.Duration

/**
 * The main button of a series (`FONCTIONNALITES.md` § 4, `SPEC.md` § 4.10): « Reprendre S02E05 —
 * 12 min restantes » or « Lire S02E05 ».
 *
 * @param resume the episode is started: playback resumes where it stopped.
 */
data class SmartPlay(
    val episode: MediaItem,
    val resume: Boolean,
) {
    /** What is left to watch of a resumed episode. */
    val remaining: Duration? get() = if (resume) episode.remaining else null

    companion object {
        /**
         * The episode the main button of a series plays, from all its episodes:
         * 1. an episode started and not finished, the one played last;
         * 2. otherwise the first episode not watched after the one watched last;
         * 3. otherwise the first episode not watched;
         * 4. otherwise (all watched, or nothing known) the first episode.
         *
         * Specials (season 0) count only when the series has nothing else. Null without episodes.
         */
        fun of(episodes: List<MediaItem>): SmartPlay? {
            val ordered =
                episodes
                    .filter { it.kind == ItemKind.Episode }
                    .sortedWith(
                        compareBy({ it.episode?.seasonNumber ?: Int.MAX_VALUE }, {
                            it.episode?.episodeNumber
                                ?: Int.MAX_VALUE
                        }),
                    )
            val regular = ordered.filter { it.episode?.seasonNumber != 0 }.ifEmpty { ordered }
            if (regular.isEmpty()) return null
            val started =
                regular
                    .filter { it.userData?.played == false && it.progress != null }
                    .maxByOrNull { it.userData?.lastPlayed ?: Instant.EPOCH }
            if (started != null) return SmartPlay(started, resume = true)
            val watched = regular.indices.filter { regular[it].userData?.played == true }
            val last = watched.maxWithOrNull(compareBy({ regular[it].userData?.lastPlayed ?: Instant.EPOCH }, { it }))
            val next =
                last?.let { index -> regular.drop(index + 1).firstOrNull { it.userData?.played != true } }
                    ?: regular.firstOrNull { it.userData?.played != true }
                    ?: regular.first()
            return SmartPlay(next, resume = false)
        }
    }
}
