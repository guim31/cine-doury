package fr.guim.cinedoury.core.data.tracks

import fr.guim.cinedoury.core.data.db.SeriesTracksDao
import fr.guim.cinedoury.core.data.db.SeriesTracksEntity
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.SeriesTracks
import fr.guim.cinedoury.core.model.TrackTraits
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * The tracks last chosen for each series, by profile, on this device (W, `SPEC.md` § 4.32). The
 * player and the details of an episode write them when the user chooses; every episode of the series
 * then starts with them ([fr.guim.cinedoury.core.model.TrackPreselection]).
 */
class SeriesTracksRepository(
    private val dao: SeriesTracksDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** One change at a time: audio and subtitles chosen in a row both stay. */
    private val writes = Mutex()

    suspend fun get(
        profile: ProfileKey,
        series: ItemId,
    ): SeriesTracks? = dao.get(profile.serverId, profile.userId, series.value)?.toModel()

    suspend fun rememberAudio(
        profile: ProfileKey,
        series: ItemId,
        traits: TrackTraits,
    ) = change(profile, series) { it.withAudio(traits) }

    /** [traits] null: « Sans sous-titres ». */
    suspend fun rememberSubtitles(
        profile: ProfileKey,
        series: ItemId,
        traits: TrackTraits?,
    ) = change(profile, series) { it.withSubtitles(traits) }

    suspend fun clear(
        serverId: String,
        userId: String?,
    ) = if (userId == null) dao.clearServer(serverId) else dao.clear(serverId, userId)

    private suspend fun change(
        profile: ProfileKey,
        series: ItemId,
        update: (SeriesTracks) -> SeriesTracks,
    ) = writes.withLock {
        val tracks = update(get(profile, series) ?: SeriesTracks())
        dao.upsert(tracks.toEntity(profile, series, clock.millis()))
    }

    private fun SeriesTracksEntity.toModel() =
        SeriesTracks(
            audio = if (audioChosen) TrackTraits(audioLanguage, hearingImpaired = audioHearingImpaired) else null,
            subtitlesChosen = subtitlesChosen,
            subtitles =
                if (subtitlesChosen && subtitlesOn) {
                    TrackTraits(subtitlesLanguage, subtitlesForced, subtitlesHearingImpaired)
                } else {
                    null
                },
        )

    private fun SeriesTracks.toEntity(
        profile: ProfileKey,
        series: ItemId,
        at: Long,
    ) = SeriesTracksEntity(
        serverId = profile.serverId,
        userId = profile.userId,
        seriesId = series.value,
        audioChosen = audio != null,
        audioLanguage = audio?.language,
        audioHearingImpaired = audio?.hearingImpaired == true,
        subtitlesChosen = subtitlesChosen,
        subtitlesOn = subtitles != null,
        subtitlesLanguage = subtitles?.language,
        subtitlesForced = subtitles?.forced == true,
        subtitlesHearingImpaired = subtitles?.hearingImpaired == true,
        updatedAt = at,
    )
}
