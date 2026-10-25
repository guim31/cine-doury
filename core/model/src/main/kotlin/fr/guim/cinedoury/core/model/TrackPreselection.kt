package fr.guim.cinedoury.core.model

/**
 * The tracks chosen before playback, from the preferences of the profile (`SPEC.md` § 4.10). The
 * user can change them in the track panel; the choice goes to the player.
 */
object TrackPreselection {
    /**
     * Audio: the preferred language (its default track, else its first); without a preferred
     * language, the default track if [PlaybackPreferences.playDefaultAudio], else the first.
     *
     * Subtitles, by [SubtitleMode]:
     * - None: none;
     * - OnlyForced: a forced track in the preferred subtitle language or the audio language;
     * - Always: a full track in the preferred subtitle language (else a forced one); without a
     *   preferred language, the default track, else the first;
     * - Smart: as Always when the audio is not in the preferred subtitle language, else as OnlyForced;
     * - Default: a forced track (as OnlyForced), else a track the file marks as default.
     */
    fun choose(
        source: MediaSource,
        preferences: PlaybackPreferences,
    ): TrackSelection {
        val audio = audio(source.audio, preferences)
        val subtitles = subtitles(source.subtitles, preferences, audio?.language)
        return TrackSelection(source.id, audio?.index, subtitles?.index)
    }

    private fun audio(
        tracks: List<MediaStream>,
        preferences: PlaybackPreferences,
    ): MediaStream? {
        val language = preferences.audioLanguage
        val inLanguage = language?.let { wanted -> tracks.filter { it.inLanguage(wanted) } }.orEmpty()
        return when {
            inLanguage.isNotEmpty() -> inLanguage.firstOrNull { it.isDefault } ?: inLanguage.first()
            preferences.playDefaultAudio -> tracks.firstOrNull { it.isDefault } ?: tracks.firstOrNull()
            else -> tracks.firstOrNull()
        }
    }

    private fun subtitles(
        tracks: List<MediaStream>,
        preferences: PlaybackPreferences,
        audioLanguage: String?,
    ): MediaStream? {
        val preferred = preferences.subtitleLanguage?.takeIf { it.isNotBlank() }
        return when (preferences.subtitleMode) {
            SubtitleMode.None -> {
                null
            }

            SubtitleMode.OnlyForced -> {
                forced(tracks, preferred, audioLanguage)
            }

            SubtitleMode.Always -> {
                full(tracks, preferred)
            }

            SubtitleMode.Smart -> {
                val foreign =
                    preferred != null && audioLanguage != null &&
                        !TechnicalSummary.sameLanguage(audioLanguage, preferred)
                if (foreign) full(tracks, preferred) else forced(tracks, preferred, audioLanguage)
            }

            SubtitleMode.Default -> {
                forced(tracks, preferred, audioLanguage)
                    ?: tracks.filter { it.isDefault }.let { defaults ->
                        preferred?.let { wanted -> defaults.firstOrNull { it.inLanguage(wanted) } }
                            ?: defaults.firstOrNull()
                    }
            }
        }
    }

    /** A forced track in one of the languages, else a forced track without language. */
    private fun forced(
        tracks: List<MediaStream>,
        vararg languages: String?,
    ): MediaStream? {
        val forced = tracks.filter { it.isForced }
        return languages.filterNotNull().firstNotNullOfOrNull { wanted -> forced.firstOrNull { it.inLanguage(wanted) } }
            ?: forced.firstOrNull { it.language == null }
    }

    private fun full(
        tracks: List<MediaStream>,
        language: String?,
    ): MediaStream? {
        if (language ==
            null
        ) {
            return tracks.firstOrNull { it.isDefault && !it.isForced } ?: tracks.firstOrNull { !it.isForced }
        }
        val inLanguage = tracks.filter { it.inLanguage(language) }
        val full = inLanguage.filterNot { it.isForced }
        return full.firstOrNull { it.isDefault } ?: full.firstOrNull { !it.isHearingImpaired } ?: full.firstOrNull()
            ?: inLanguage.firstOrNull()
    }

    private fun MediaStream.inLanguage(code: String): Boolean =
        language?.let { TechnicalSummary.sameLanguage(it, code) } == true
}
