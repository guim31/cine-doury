package fr.guim.cinedoury.core.model

/** What a person did on an item. */
enum class CreditType {
    Actor,
    GuestStar,
    Director,
    Writer,
    Producer,
    Creator,
    Composer,
    Other,
}

/** A person in the credits of an item: [role] is the character of an actor. */
data class Credit(
    val id: ItemId,
    val name: String,
    val type: CreditType,
    val role: String? = null,
    val image: ImageRef? = null,
)

enum class SeriesStatus {
    Continuing,
    Ended,
}

/** A trailer on the web: opened in the app of its site (YouTube). */
data class RemoteTrailer(
    val url: String,
    val name: String? = null,
) {
    /** The YouTube video id of [url], or null when it is not a YouTube link. */
    val youTubeId: String?
        get() {
            val match = YOUTUBE.find(url) ?: return null
            return match.groupValues[1]
        }

    private companion object {
        val YOUTUBE = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{6,})""")
    }
}

/** How subtitles are chosen when a video starts (Jellyfin: `SubtitlePlaybackMode`). */
enum class SubtitleMode {
    /** The tracks the file marks as default or forced. */
    Default,

    /** Always the subtitles of the preferred language. */
    Always,

    /** Forced subtitles only (signs, foreign dialogue). */
    OnlyForced,

    None,

    /** Subtitles of the preferred language when the audio is in another language. */
    Smart,
}

/**
 * The playback preferences of the profile, as set on the server (`/Users/Me`, `Configuration`).
 *
 * @param audioLanguage ISO 639-2 code of the preferred audio language; null for the file's default.
 * @param subtitleLanguage ISO 639-2 code of the preferred subtitle language; null for none.
 * @param playDefaultAudio take the track the file marks as default when no language is preferred.
 */
data class PlaybackPreferences(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.Default,
    val playDefaultAudio: Boolean = true,
)

/**
 * What to play: a version of an item and its tracks, chosen before playback (`FONCTIONNALITES.md`
 * § 4) and handed to the player (J4).
 *
 * @param subtitleIndex the stream index of the subtitles; null without subtitles.
 */
data class TrackSelection(
    val sourceId: String,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
)
