package fr.guim.cinedoury.core.model

/**
 * What a track is, not where it is: its language and its nature. The indices of the streams change
 * from one file to the next, so a choice made on one episode is found on the next by these (W).
 *
 * @param language ISO 639-2 code; null for a track without language.
 */
data class TrackTraits(
    val language: String?,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
) {
    companion object {
        fun of(
            stream: MediaStream,
        ): TrackTraits = TrackTraits(stream.language, stream.isForced, stream.isHearingImpaired)
    }
}

/**
 * The tracks last chosen by hand for a series, by a profile (W, `SPEC.md` § 4.32): during playback or
 * on the details of an episode. Each episode of the series starts with them, when it has them.
 *
 * @param audio the audio chosen; null: none chosen, the preferences of the profile decide.
 * @param subtitlesChosen the subtitles were chosen by hand, [subtitles] says which.
 * @param subtitles the subtitles chosen; null with [subtitlesChosen]: « Sans sous-titres ».
 */
data class SeriesTracks(
    val audio: TrackTraits? = null,
    val subtitlesChosen: Boolean = false,
    val subtitles: TrackTraits? = null,
) {
    fun withAudio(traits: TrackTraits): SeriesTracks = copy(audio = traits)

    fun withSubtitles(traits: TrackTraits?): SeriesTracks = copy(subtitlesChosen = true, subtitles = traits)
}
