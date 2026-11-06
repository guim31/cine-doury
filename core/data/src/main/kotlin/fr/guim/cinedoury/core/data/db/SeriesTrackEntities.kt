package fr.guim.cinedoury.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/**
 * The tracks a profile last chose by hand for a series (W, `SPEC.md` § 4.32): by language and nature,
 * never by index (the indices change from one file to the next).
 */
@Entity(tableName = "series_tracks", primaryKeys = ["server_id", "user_id", "series_id"])
data class SeriesTracksEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
    /** An audio track was chosen; [audioLanguage] is null for a track without language. */
    @ColumnInfo(name = "audio_chosen") val audioChosen: Boolean,
    @ColumnInfo(name = "audio_language") val audioLanguage: String?,
    @ColumnInfo(name = "audio_hearing_impaired") val audioHearingImpaired: Boolean,
    /** Subtitles were chosen; [subtitlesOn] false: « Sans sous-titres ». */
    @ColumnInfo(name = "subtitles_chosen") val subtitlesChosen: Boolean,
    @ColumnInfo(name = "subtitles_on") val subtitlesOn: Boolean,
    @ColumnInfo(name = "subtitles_language") val subtitlesLanguage: String?,
    @ColumnInfo(name = "subtitles_forced") val subtitlesForced: Boolean,
    @ColumnInfo(name = "subtitles_hearing_impaired") val subtitlesHearingImpaired: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface SeriesTracksDao {
    @Query("SELECT * FROM series_tracks WHERE server_id = :serverId AND user_id = :userId AND series_id = :seriesId")
    suspend fun get(
        serverId: String,
        userId: String,
        seriesId: String,
    ): SeriesTracksEntity?

    @Upsert
    suspend fun upsert(entity: SeriesTracksEntity)

    @Query("DELETE FROM series_tracks WHERE server_id = :serverId AND user_id = :userId")
    suspend fun clear(
        serverId: String,
        userId: String,
    )

    @Query("DELETE FROM series_tracks WHERE server_id = :serverId")
    suspend fun clearServer(serverId: String)
}
