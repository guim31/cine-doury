package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.model.UserId
import fr.guim.cinedoury.core.model.VideoRange
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.serializer.toUUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import fr.guim.cinedoury.core.model.ImageType as ModelImageType
import org.jellyfin.sdk.model.api.ImageType as JellyfinImageType
import org.jellyfin.sdk.model.api.MediaStream as JellyfinMediaStream

/** Jellyfin ids travel as 32 hexadecimal digits, without dashes, as the server writes them. */
internal fun UUID.toJellyfinId(): String = toString().replace("-", "")

internal fun String.toJellyfinUuid(): UUID = toUUID()

/** Jellyfin durations are in ticks of 100 ns. */
internal fun Long.ticksToDuration(): Duration = (this / TICKS_PER_MICROSECOND).microseconds

private const val TICKS_PER_MICROSECOND = 10

internal fun UserDto.toModel() =
    User(
        id = UserId(id.toJellyfinId()),
        name = name.orEmpty(),
        isAdministrator = policy?.isAdministrator == true,
        hasPassword = hasPassword == true,
        image = primaryImageTag?.let { ImageRef(ItemId(id.toJellyfinId()), ModelImageType.Primary, it) },
    )

/** Music, books, photos, playlists and live TV are out of scope (`SPEC.md` § 2): no library for them. */
internal fun BaseItemDto.toLibrary(): Library? {
    val kind =
        when (collectionType) {
            CollectionType.MOVIES -> LibraryKind.Movies

            CollectionType.TVSHOWS -> LibraryKind.Shows

            CollectionType.BOXSETS -> LibraryKind.Collections

            CollectionType.HOMEVIDEOS -> LibraryKind.HomeVideos

            CollectionType.MUSICVIDEOS, CollectionType.FOLDERS, CollectionType.UNKNOWN, null -> LibraryKind.Mixed

            CollectionType.MUSIC, CollectionType.BOOKS, CollectionType.PHOTOS,
            CollectionType.PLAYLISTS, CollectionType.LIVETV, CollectionType.TRAILERS,
            -> return null
        }
    return Library(
        id = LibraryId(id.toJellyfinId()),
        name = name.orEmpty(),
        kind = kind,
        image = image(JellyfinImageType.PRIMARY),
    )
}

internal fun BaseItemDto.toMediaItem(): MediaItem =
    MediaItem(
        id = ItemId(id.toJellyfinId()),
        kind = type.toItemKind(),
        title = name.orEmpty(),
        sortTitle = sortName,
        originalTitle = originalTitle,
        year = productionYear,
        premiereDate = premiereDate?.toLocalDate(),
        overview = overview,
        runtime = runTimeTicks?.ticksToDuration(),
        officialRating = officialRating,
        communityRating = communityRating,
        genres = genres.orEmpty(),
        images =
            ItemImages(
                primary = image(JellyfinImageType.PRIMARY),
                backdrops =
                    backdropImageTags.orEmpty().mapIndexed { index, tag ->
                        ImageRef(
                            ItemId(id.toJellyfinId()),
                            ModelImageType.Backdrop,
                            tag,
                            index,
                            blurHash(JellyfinImageType.BACKDROP, tag),
                        )
                    },
                logo = image(JellyfinImageType.LOGO),
                thumb = image(JellyfinImageType.THUMB),
            ),
        userData = userData?.toModel(),
        episode =
            if (type == BaseItemKind.EPISODE || type == BaseItemKind.SEASON) {
                EpisodeInfo(
                    seriesId = seriesId?.let { ItemId(it.toJellyfinId()) },
                    seriesTitle = seriesName,
                    seasonId = seasonId?.let { ItemId(it.toJellyfinId()) },
                    seasonNumber = if (type == BaseItemKind.SEASON) indexNumber else parentIndexNumber,
                    episodeNumber = if (type == BaseItemKind.EPISODE) indexNumber else null,
                )
            } else {
                null
            },
        childCount = childCount,
        mediaSources = mediaSources.orEmpty().map { it.toModel() },
    )

private fun BaseItemDto.image(type: JellyfinImageType): ImageRef? {
    val tag = imageTags?.get(type) ?: return null
    val modelType =
        when (type) {
            JellyfinImageType.LOGO -> ModelImageType.Logo
            JellyfinImageType.THUMB -> ModelImageType.Thumb
            JellyfinImageType.BACKDROP -> ModelImageType.Backdrop
            else -> ModelImageType.Primary
        }
    return ImageRef(ItemId(id.toJellyfinId()), modelType, tag, blurHash = blurHash(type, tag))
}

private fun BaseItemDto.blurHash(type: JellyfinImageType, tag: String): String? = imageBlurHashes?.get(type)?.get(tag)

private fun BaseItemKind.toItemKind(): ItemKind =
    when (this) {
        BaseItemKind.MOVIE -> ItemKind.Movie
        BaseItemKind.SERIES -> ItemKind.Series
        BaseItemKind.SEASON -> ItemKind.Season
        BaseItemKind.EPISODE -> ItemKind.Episode
        BaseItemKind.BOX_SET -> ItemKind.Collection
        BaseItemKind.PERSON -> ItemKind.Person
        BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO, BaseItemKind.TRAILER -> ItemKind.Video
        BaseItemKind.FOLDER, BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW -> ItemKind.Folder
        else -> ItemKind.Other
    }

private fun UserItemDataDto.toModel() =
    UserData(
        played = played,
        playbackPosition = playbackPositionTicks.ticksToDuration(),
        playCount = playCount,
        isFavorite = isFavorite,
        unplayedCount = unplayedItemCount,
    )

private fun MediaSourceInfo.toModel() =
    MediaSource(
        id = id.orEmpty(),
        name = name,
        container = container,
        sizeBytes = size,
        streams = mediaStreams.orEmpty().map { it.toModel() },
    )

private fun JellyfinMediaStream.toModel(): MediaStream {
    val streamType =
        when (type) {
            MediaStreamType.VIDEO -> StreamType.Video
            MediaStreamType.AUDIO -> StreamType.Audio
            MediaStreamType.SUBTITLE -> StreamType.Subtitle
            else -> StreamType.Other
        }
    return MediaStream(
        index = index,
        type = streamType,
        codec = codec,
        language = language,
        title = title,
        isDefault = isDefault,
        isForced = isForced,
        isExternal = isExternal,
        channels = channels,
        width = width,
        height = height,
        bitDepth = bitDepth,
        videoRange = if (streamType == StreamType.Video) videoRangeType.toModel() else null,
    )
}

private fun VideoRangeType.toModel(): VideoRange =
    when (this) {
        VideoRangeType.SDR -> VideoRange.Sdr

        VideoRangeType.HDR10 -> VideoRange.Hdr10

        VideoRangeType.HDR10_PLUS -> VideoRange.Hdr10Plus

        VideoRangeType.HLG -> VideoRange.Hlg

        VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_HLG,
        VideoRangeType.DOVI_WITH_SDR, VideoRangeType.DOVI_WITH_EL, VideoRangeType.DOVI_WITH_HDR10_PLUS,
        VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
        -> VideoRange.DolbyVision

        VideoRangeType.UNKNOWN, VideoRangeType.DOVI_INVALID -> VideoRange.Unknown
    }
