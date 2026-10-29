package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.data.library.icon
import fr.guim.cinedoury.core.data.prefs.InterfaceSize
import fr.guim.cinedoury.core.data.prefs.NetworkMode
import fr.guim.cinedoury.core.data.prefs.TextSize
import fr.guim.cinedoury.core.data.prefs.ThemeChoice
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.model.AspectMode
import fr.guim.cinedoury.core.model.DirectPlayMode
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.Qualities
import fr.guim.cinedoury.core.model.SegmentAction
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleColor
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.model.SubtitlePosition
import fr.guim.cinedoury.core.model.SubtitleScale
import fr.guim.cinedoury.core.ui.toDateText
import fr.guim.cinedoury.core.ui.toTimeText
import fr.guim.cinedoury.core.ui.vector
import fr.guim.cinedoury.core.update.AppVersion
import fr.guim.cinedoury.feature.player.label
import fr.guim.cinedoury.feature.player.scaleLabel
import fr.guim.cinedoury.feature.settings.page.SettingItem
import fr.guim.cinedoury.feature.update.UpdatePhase
import java.time.ZoneId
import fr.guim.cinedoury.feature.player.R as PlayerR

/** What a page of settings asks the app to open. */
internal class PageCallbacks(
    val navigation: SettingsNavigation,
    val onForget: () -> Unit,
    val showDebug: Boolean,
    val systemReducedMotion: Boolean,
)

/** The lines of [section], each with what it does and its default (`FONCTIONNALITES.md` § 12). */
@Composable
internal fun pageItems(
    section: SettingsSection,
    state: SettingsState,
    actions: SettingsActions,
    callbacks: PageCallbacks,
): List<SettingItem> =
    when (section) {
        SettingsSection.Playback -> playbackItems(state, actions)
        SettingsSection.Picture -> pictureItems(state, actions)
        SettingsSection.Sound -> soundItems(state, actions)
        SettingsSection.Subtitles -> subtitleItems(state, actions)
        SettingsSection.Segments -> segmentItems(state, actions)
        SettingsSection.Browsing -> browsingItems(state, actions, callbacks.navigation)
        SettingsSection.Libraries -> libraryItems(state, actions)
        SettingsSection.Appearance -> appearanceItems(state, actions, callbacks.systemReducedMotion)
        SettingsSection.Profile -> profileItems(state, actions, callbacks.navigation)
        SettingsSection.Network -> networkItems(state, actions, callbacks)
        SettingsSection.Device -> deviceItems(state, callbacks.navigation)
        SettingsSection.Advanced -> advancedItems(actions, callbacks)
    }

/** « Explication. Par défaut : valeur. » */
@Composable
private fun described(
    explanation: String,
    default: String,
): String = stringResource(R.string.setting_default, explanation, default)

@Composable
private fun profileSection(
    state: SettingsState,
    onServer: Boolean = false,
) = SettingItem.Section(
    key = if (onServer) "profile-server" else "profile",
    title =
        stringResource(
            R.string.section_for_profile,
            state.session
                ?.profile
                ?.user
                ?.name
                .orEmpty(),
        ),
    note =
        stringResource(
            if (onServer) R.string.section_for_profile_server_note else R.string.section_for_profile_note,
        ),
)

@Composable
private fun deviceSection() =
    SettingItem.Section(
        key = "device",
        title = stringResource(R.string.section_for_device),
        note = stringResource(R.string.section_for_device_note),
    )

@Composable
private fun on(checked: Boolean): String = stringResource(if (checked) R.string.setting_on else R.string.setting_off)

/** A choice among [values], shown by [label]; the current value is [current]. */
@Composable
private fun <T> choice(
    key: String,
    title: String,
    explanation: String,
    values: List<T>,
    current: T,
    default: T,
    label: @Composable (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
): SettingItem.Choice {
    val labels = values.map { label(it) }
    return SettingItem.Choice(
        key = key,
        title = title,
        description = described(explanation, label(default)),
        options = labels,
        selected = values.indexOf(current).coerceAtLeast(0),
        onSelect = { onSelect(values[it]) },
        enabled = enabled,
    )
}

@Composable
private fun toggle(
    key: String,
    title: String,
    explanation: String,
    checked: Boolean,
    default: Boolean,
    onToggle: () -> Unit,
) = SettingItem.Toggle(key, title, described(explanation, on(default)), checked, onToggle)

// Lecture.

@Composable
private fun playbackItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> {
    val settings = state.player
    val defaults = PlayerSettings()
    val qualities = listOf<Int?>(null) + Qualities.BITRATES
    return listOf(
        profileSection(state),
        choice(
            "seek-back",
            stringResource(R.string.playback_seek_back),
            stringResource(R.string.playback_seek_back_description),
            PlayerSettings.SEEK_STEPS,
            settings.seekBackSeconds,
            defaults.seekBackSeconds,
            { stringResource(R.string.value_seconds, it) },
        ) { value -> actions.updatePlayer { it.copy(seekBackSeconds = value) } },
        choice(
            "seek-forward",
            stringResource(R.string.playback_seek_forward),
            stringResource(R.string.playback_seek_forward_description),
            PlayerSettings.SEEK_STEPS,
            settings.seekForwardSeconds,
            defaults.seekForwardSeconds,
            { stringResource(R.string.value_seconds, it) },
        ) { value -> actions.updatePlayer { it.copy(seekForwardSeconds = value) } },
        choice(
            "watched",
            stringResource(R.string.playback_watched),
            stringResource(R.string.playback_watched_description),
            PlayerSettings.WATCHED_THRESHOLDS,
            settings.watchedThreshold,
            defaults.watchedThreshold,
            { stringResource(R.string.value_percent, Math.round(it * PERCENT)) },
        ) { value -> actions.updatePlayer { it.copy(watchedThreshold = value) } },
        deviceSection(),
        choice(
            "local-quality",
            stringResource(R.string.playback_local_quality),
            stringResource(R.string.playback_local_quality_description),
            qualities,
            settings.localQuality,
            defaults.localQuality,
            { quality(it) },
        ) { value -> actions.updatePlayer { it.copy(localQuality = value) } },
        choice(
            "remote-quality",
            stringResource(R.string.playback_remote_quality),
            stringResource(R.string.playback_remote_quality_description),
            qualities,
            settings.remoteQuality,
            defaults.remoteQuality,
            { quality(it) },
        ) { value -> actions.updatePlayer { it.copy(remoteQuality = value) } },
        choice(
            "ceiling",
            stringResource(R.string.playback_ceiling),
            stringResource(R.string.playback_ceiling_description),
            PlayerSettings.CEILINGS,
            settings.bitrateCeiling,
            defaults.bitrateCeiling,
            { quality(it) },
        ) { value -> actions.updatePlayer { it.copy(bitrateCeiling = value) } },
        choice(
            "direct",
            stringResource(R.string.playback_direct),
            stringResource(R.string.playback_direct_description),
            DirectPlayMode.entries,
            settings.directPlay,
            defaults.directPlay,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(directPlay = value) } },
        toggle(
            "burn-in",
            stringResource(R.string.playback_burn_in),
            stringResource(R.string.playback_burn_in_description),
            settings.burnInAss,
            defaults.burnInAss,
        ) { actions.updatePlayer { it.copy(burnInAss = !it.burnInAss) } },
    )
}

@Composable
private fun quality(bitrate: Int?): String =
    if (bitrate ==
        null
    ) {
        stringResource(R.string.quality_original)
    } else {
        stringResource(R.string.quality_mbps, bitrate / MEGABIT)
    }

@Composable
private fun DirectPlayMode.label(): String =
    stringResource(
        when (this) {
            DirectPlayMode.Auto -> R.string.direct_auto
            DirectPlayMode.Forced -> R.string.direct_forced
            DirectPlayMode.Never -> R.string.direct_never
        },
    )

// Image.

@Composable
private fun pictureItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> {
    val settings = state.player
    val defaults = PlayerSettings()
    val screen = state.capabilities?.display?.hdr
    val notShown = stringResource(R.string.picture_not_shown)

    fun shown(
        range: (Set<DynamicRange>) -> Boolean,
        text: String,
    ) = if (screen != null && !range(screen)) "$text $notShown" else text
    val hdr =
        shown({
            it.any { range ->
                range != DynamicRange.DolbyVision && range != DynamicRange.Sdr
            }
        }, stringResource(R.string.picture_hdr_description))
    val dolby = shown({ DynamicRange.DolbyVision in it }, stringResource(R.string.picture_dolby_vision_description))
    return listOf(
        deviceSection(),
        toggle(
            "frame-rate",
            stringResource(R.string.picture_frame_rate),
            stringResource(R.string.picture_frame_rate_description),
            settings.matchFrameRate,
            defaults.matchFrameRate,
        ) { actions.updatePlayer { it.copy(matchFrameRate = !it.matchFrameRate) } },
        toggle(
            "resolution",
            stringResource(R.string.picture_resolution),
            stringResource(R.string.picture_resolution_description),
            settings.matchResolution,
            defaults.matchResolution,
        ) { actions.updatePlayer { it.copy(matchResolution = !it.matchResolution) } },
        toggle("hdr", stringResource(R.string.picture_hdr), hdr, settings.allowHdr, defaults.allowHdr) {
            actions.updatePlayer { it.copy(allowHdr = !it.allowHdr) }
        },
        toggle(
            "dolby-vision",
            stringResource(R.string.picture_dolby_vision),
            dolby,
            settings.allowDolbyVision,
            defaults.allowDolbyVision,
        ) { actions.updatePlayer { it.copy(allowDolbyVision = !it.allowDolbyVision) } },
        choice(
            "aspect",
            stringResource(R.string.picture_aspect),
            stringResource(R.string.picture_aspect_description),
            AspectMode.entries,
            settings.aspect,
            defaults.aspect,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(aspect = value) } },
    )
}

// Son.

/** The languages offered, as ISO 639-2 codes (Jellyfin keeps the bibliographic ones: « fre »). */
private val LANGUAGES = listOf("fre", "eng", "jpn", "ger", "spa", "ita")

@Composable
private fun language(code: String?): String =
    when (code) {
        null -> stringResource(R.string.language_default)
        "fre", "fra" -> stringResource(R.string.language_fre)
        "eng" -> stringResource(R.string.language_eng)
        "jpn" -> stringResource(R.string.language_jpn)
        "ger", "deu" -> stringResource(R.string.language_ger)
        "spa" -> stringResource(R.string.language_spa)
        "ita" -> stringResource(R.string.language_ita)
        else -> code
    }

/** A setting kept on the server: a line that says it is being read, or could not be. */
@Composable
private fun serverChoice(
    state: SettingsState,
    key: String,
    title: String,
    build: @Composable (PlaybackPreferences) -> SettingItem,
): SettingItem =
    when (val server = state.server) {
        is ServerPreferences.Ready -> {
            build(server.preferences)
        }

        ServerPreferences.Loading -> {
            SettingItem.Action(
                key = key,
                title = title,
                description = stringResource(R.string.server_loading),
                onClick = {},
            )
        }

        is ServerPreferences.Failed -> {
            SettingItem.Action(
                key = key,
                title = title,
                description = stringResource(R.string.server_failed),
                onClick = {},
            )
        }
    }

@Composable
private fun soundItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> {
    val settings = state.player
    val defaults = PlayerSettings()
    val output = state.capabilities?.passthrough.orEmpty()
    val title = stringResource(R.string.sound_language)
    return buildList {
        add(profileSection(state, onServer = true))
        add(
            serverChoice(state, "audio-language", title) { preferences ->
                choice(
                    "audio-language",
                    title,
                    stringResource(R.string.sound_language_description),
                    listOf<String?>(null) + LANGUAGES,
                    preferences.audioLanguage?.let(::bibliographic),
                    null,
                    { language(it) },
                ) { value -> actions.updateServerPreferences { it.copy(audioLanguage = value) } }
            },
        )
        add(deviceSection())
        add(
            toggle(
                "stereo",
                stringResource(R.string.sound_stereo),
                stringResource(R.string.sound_stereo_description),
                settings.forceStereo,
                defaults.forceStereo,
            ) { actions.updatePlayer { it.copy(forceStereo = !it.forceStereo) } },
        )
        add(
            SettingItem.Stepper(
                key = "audio-offset",
                title = stringResource(R.string.sound_offset),
                description =
                    described(
                        stringResource(R.string.sound_offset_description),
                        stringResource(R.string.sound_offset_value, 0),
                    ),
                value = stringResource(R.string.sound_offset_value, settings.audioOffsetMillis),
                canDecrease = settings.audioOffsetMillis > -PlayerSettings.MAX_AUDIO_OFFSET,
                canIncrease = settings.audioOffsetMillis < PlayerSettings.MAX_AUDIO_OFFSET,
                onStep = { forward ->
                    actions.updatePlayer {
                        val step = if (forward) PlayerSettings.AUDIO_OFFSET_STEP else -PlayerSettings.AUDIO_OFFSET_STEP
                        it.copy(
                            audioOffsetMillis =
                                (it.audioOffsetMillis + step).coerceIn(
                                    -PlayerSettings.MAX_AUDIO_OFFSET,
                                    PlayerSettings.MAX_AUDIO_OFFSET,
                                ),
                        )
                    }
                },
            ),
        )
        add(SettingItem.Section(key = "passthrough", title = stringResource(R.string.sound_passthrough)))
        PassthroughFormat.entries.forEach { format ->
            add(
                toggle(
                    "passthrough-${format.name}",
                    format.label(),
                    stringResource(
                        if (format in
                            output
                        ) {
                            R.string.passthrough_available
                        } else {
                            R.string.passthrough_unavailable
                        },
                    ),
                    format in settings.passthrough && !settings.forceStereo,
                    true,
                ) {
                    actions.updatePlayer {
                        it.copy(
                            passthrough =
                                if (format in
                                    it.passthrough
                                ) {
                                    it.passthrough - format
                                } else {
                                    it.passthrough + format
                                },
                        )
                    }
                },
            )
        }
    }
}

/** « fra » and « fre » are the same language: the list offers the bibliographic code. */
private fun bibliographic(code: String): String =
    when (code) {
        "fra" -> "fre"
        "deu" -> "ger"
        else -> code
    }

// Sous-titres.

@Composable
private fun subtitleItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> {
    val style = state.player.subtitles
    val modeTitle = stringResource(R.string.subtitles_mode)
    val languageTitle = stringResource(R.string.subtitles_language)
    val modes =
        listOf(
            SubtitleMode.Default,
            SubtitleMode.None,
            SubtitleMode.OnlyForced,
            SubtitleMode.Always,
            SubtitleMode.Smart,
        )
    return listOf(
        profileSection(state, onServer = true),
        serverChoice(state, "subtitle-mode", modeTitle) { preferences ->
            choice(
                "subtitle-mode",
                modeTitle,
                stringResource(R.string.mode_smart_description),
                modes,
                preferences.subtitleMode,
                PlaybackPreferences().subtitleMode,
                { it.label() },
            ) { value -> actions.updateServerPreferences { it.copy(subtitleMode = value) } }
        },
        serverChoice(state, "subtitle-language", languageTitle) { preferences ->
            choice(
                "subtitle-language",
                languageTitle,
                stringResource(R.string.subtitles_language_description),
                listOf<String?>(null) + LANGUAGES,
                preferences.subtitleLanguage?.let(::bibliographic),
                null,
                { it?.let { code -> language(code) } ?: stringResource(R.string.language_none) },
            ) { value -> actions.updateServerPreferences { it.copy(subtitleLanguage = value) } }
        },
        SettingItem.Section(
            key = "look",
            title = stringResource(R.string.subtitles_look),
            note = stringResource(R.string.subtitles_look_note),
        ),
        SettingItem.Stepper(
            key = "subtitle-size",
            title = stringResource(PlayerR.string.player_subtitle_size),
            description =
                described(
                    stringResource(R.string.subtitles_size_description),
                    stringResource(R.string.value_percent, PERCENT),
                ),
            value = style.scaleLabel(),
            canDecrease = style.scale > SubtitleScale.MIN,
            canIncrease = style.scale < SubtitleScale.MAX,
            onStep = { forward -> actions.updatePlayer { it.copy(subtitles = it.subtitles.step(forward)) } },
        ),
        choice(
            "subtitle-color",
            stringResource(PlayerR.string.player_subtitle_color),
            stringResource(R.string.subtitles_style_description),
            SubtitleColor.entries,
            style.color,
            SubtitleColor.White,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(subtitles = it.subtitles.copy(color = value)) } },
        choice(
            "subtitle-edge",
            stringResource(PlayerR.string.player_subtitle_edge),
            stringResource(R.string.subtitles_style_description),
            SubtitleEdge.entries,
            style.edge,
            SubtitleEdge.Outline,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(subtitles = it.subtitles.copy(edge = value)) } },
        choice(
            "subtitle-background",
            stringResource(PlayerR.string.player_subtitle_background),
            stringResource(R.string.subtitles_style_description),
            SubtitleBackground.entries,
            style.background,
            SubtitleBackground.None,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(subtitles = it.subtitles.copy(background = value)) } },
        choice(
            "subtitle-position",
            stringResource(PlayerR.string.player_subtitle_position),
            stringResource(R.string.subtitles_position_description),
            SubtitlePosition.entries,
            style.position,
            SubtitlePosition.Bottom,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(subtitles = it.subtitles.copy(position = value)) } },
    )
}

@Composable
private fun SubtitleMode.label(): String =
    stringResource(
        when (this) {
            SubtitleMode.Default -> R.string.mode_default
            SubtitleMode.None -> R.string.mode_none
            SubtitleMode.OnlyForced -> R.string.mode_forced
            SubtitleMode.Always -> R.string.mode_always
            SubtitleMode.Smart -> R.string.mode_smart
        },
    )

// Intro et générique.

@Composable
private fun segmentItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> {
    val settings = state.player
    val defaults = PlayerSettings()
    return listOf(
        profileSection(state),
        choice(
            "segments",
            stringResource(R.string.segments_action),
            stringResource(R.string.segments_action_description),
            SegmentAction.entries,
            settings.segments,
            defaults.segments,
            { it.label() },
        ) { value -> actions.updatePlayer { it.copy(segments = value) } },
        toggle(
            "autoplay",
            stringResource(R.string.segments_autoplay),
            stringResource(R.string.segments_autoplay_description),
            settings.autoplayNext,
            defaults.autoplayNext,
        ) { actions.updatePlayer { it.copy(autoplayNext = !it.autoplayNext) } },
        choice(
            "countdown",
            stringResource(R.string.segments_countdown),
            stringResource(R.string.segments_countdown_description),
            PlayerSettings.COUNTDOWN_STEPS,
            settings.nextCountdownSeconds,
            defaults.nextCountdownSeconds,
            { stringResource(R.string.value_seconds, it) },
        ) { value -> actions.updatePlayer { it.copy(nextCountdownSeconds = value) } },
        choice(
            "still-watching",
            stringResource(R.string.segments_still),
            stringResource(R.string.segments_still_description),
            PlayerSettings.STILL_WATCHING_STEPS,
            settings.stillWatchingAfter,
            defaults.stillWatchingAfter,
            { if (it == 0) stringResource(R.string.value_never) else stringResource(R.string.value_episodes, it) },
        ) { value -> actions.updatePlayer { it.copy(stillWatchingAfter = value) } },
    )
}

// Accueil et navigation.

@Composable
private fun browsingItems(
    state: SettingsState,
    actions: SettingsActions,
    navigation: SettingsNavigation,
): List<SettingItem> {
    val browsing = state.browsing
    val defaults =
        fr.guim.cinedoury.core.data.prefs
            .Browsing()
    return listOf(
        profileSection(state),
        SettingItem.Action(
            key = "customize-home",
            title = stringResource(R.string.customize_home),
            description = stringResource(R.string.customize_home_description),
            icon = CineIcons.Edit,
            onClick = navigation::customizeHome,
        ),
        toggle(
            "tonight",
            stringResource(R.string.browsing_tonight),
            stringResource(R.string.browsing_tonight_description),
            browsing.tonightMosaic,
            defaults.tonightMosaic,
        ) { actions.updateBrowsing { it.copy(tonightMosaic = !it.tonightMosaic) } },
        toggle(
            "banner",
            stringResource(R.string.browsing_banner),
            stringResource(R.string.browsing_banner_description),
            browsing.featuredBanner,
            defaults.featuredBanner,
        ) { actions.updateBrowsing { it.copy(featuredBanner = !it.featuredBanner) } },
        choice(
            "default-view",
            stringResource(R.string.browsing_view),
            stringResource(R.string.browsing_view_description),
            LibraryView.entries,
            browsing.defaultView,
            defaults.defaultView,
            { it.label() },
        ) { value -> actions.updateBrowsing { it.copy(defaultView = value) } },
        toggle(
            "poster-titles",
            stringResource(R.string.browsing_titles),
            stringResource(R.string.browsing_titles_description),
            browsing.posterTitles,
            defaults.posterTitles,
        ) { actions.updateBrowsing { it.copy(posterTitles = !it.posterTitles) } },
        toggle(
            "unwatched",
            stringResource(R.string.browsing_unwatched),
            stringResource(R.string.browsing_unwatched_description),
            browsing.unwatchedMarks,
            defaults.unwatchedMarks,
        ) { actions.updateBrowsing { it.copy(unwatchedMarks = !it.unwatchedMarks) } },
    )
}

@Composable
private fun LibraryView.label(): String =
    stringResource(
        when (this) {
            LibraryView.Posters -> R.string.view_posters
            LibraryView.SmallPosters -> R.string.view_small_posters
            LibraryView.LargePosters -> R.string.view_large_posters
            LibraryView.List -> R.string.view_list
            LibraryView.Thumbnails -> R.string.view_thumbnails
        },
    )

// Bibliothèques.

@Composable
private fun libraryItems(
    state: SettingsState,
    actions: SettingsActions,
): List<SettingItem> =
    buildList {
        val libraries = state.libraries
        add(
            SettingItem.Section(
                key = "rail",
                title =
                    stringResource(
                        R.string.section_rail,
                        state.session
                            ?.profile
                            ?.user
                            ?.name
                            .orEmpty(),
                    ),
                note = stringResource(R.string.section_rail_note),
            ),
        )
        val places = listOf<Int?>(null) + libraries.indices
        libraries.forEachIndexed { index, library ->
            val labels = places.map { place -> place(place) }
            add(
                SettingItem.Choice(
                    key = "place-${library.id.value}",
                    title = library.name,
                    description = stringResource(R.string.library_place_description),
                    options = labels,
                    selected = if (library.id in state.hidden) 0 else index + 1,
                    onSelect = { actions.placeLibrary(library.id, places[it]) },
                ),
            )
        }
        add(SettingItem.Section(key = "icons", title = stringResource(R.string.section_icons)))
        libraries.forEach { library ->
            val automatic = LibraryIcon.defaultFor(library.kind, library.name)
            val options = listOf<LibraryIcon?>(null) + LibraryIcon.entries
            val chosen = state.chosen[library.id]
            add(
                SettingItem.Choice(
                    key = "icon-${library.id.value}",
                    title = library.name,
                    description =
                        stringResource(
                            if (chosen != null) R.string.library_icon_chosen else R.string.library_icon_automatic,
                        ),
                    options =
                        options.map {
                            it?.label() ?: stringResource(
                                R.string.library_icon_auto,
                                automatic.label(),
                            )
                        },
                    selected = options.indexOf(chosen),
                    onSelect = { actions.setIcon(library.id, options[it]) },
                    // Each icon with its name; « Automatique » with the one the name gives.
                    optionIcons = options.map { (it ?: automatic).vector },
                    icon = (chosen ?: automatic).vector,
                ),
            )
        }
        add(
            SettingItem.Action(
                key = "index",
                title = stringResource(R.string.index_title),
                description = state.index.text(),
                icon = CineIcons.Retry,
                onClick = actions::rebuildIndex,
            ),
        )
    }

@Composable
private fun place(place: Int?): String =
    when (place) {
        null -> stringResource(R.string.library_hidden)
        0 -> stringResource(R.string.library_position_first)
        else -> stringResource(R.string.library_position, place + 1)
    }

/**
 * « À jour : 1 523 films et 10 112 épisodes, le 25 oct. 2026 à 14 h 02 », « Mise à jour : 3 200 sur
 * 11 500 titres », a failure, or not built yet: a discreet line, never in the way.
 */
@Composable
internal fun IndexStatus.text(): String {
    val total = total
    val updated = updatedAt?.atZone(ZoneId.systemDefault())
    return when {
        running && total != null -> {
            stringResource(R.string.index_running, read, total)
        }

        running -> {
            stringResource(R.string.index_starting)
        }

        error != null -> {
            stringResource(R.string.index_failed)
        }

        updated != null -> {
            stringResource(
                R.string.index_ready,
                pluralStringResource(R.plurals.films_count, films, films),
                pluralStringResource(R.plurals.episodes_count, episodes, episodes),
                updated.toLocalDate().toDateText(),
                updated.toLocalTime().toTimeText(),
            )
        }

        else -> {
            stringResource(R.string.index_empty)
        }
    }
}

@Composable
internal fun LibraryIcon.label(): String =
    stringResource(
        when (this) {
            LibraryIcon.Movies -> R.string.icon_movies
            LibraryIcon.Shows -> R.string.icon_shows
            LibraryIcon.Animation -> R.string.icon_animation
            LibraryIcon.Anime -> R.string.icon_anime
            LibraryIcon.Documentaries -> R.string.icon_documentaries
            LibraryIcon.Stage -> R.string.icon_stage
            LibraryIcon.Sport -> R.string.icon_sport
            LibraryIcon.OnlineVideo -> R.string.icon_online
            LibraryIcon.Kids -> R.string.icon_kids
            LibraryIcon.Collections -> R.string.icon_collections
            LibraryIcon.HomeVideos -> R.string.icon_home_videos
            LibraryIcon.Nature -> R.string.icon_nature
            LibraryIcon.MusicVideos -> R.string.icon_music_videos
            LibraryIcon.Comedy -> R.string.icon_comedy
            LibraryIcon.TvPrograms -> R.string.icon_tv_programs
            LibraryIcon.Learning -> R.string.icon_learning
            LibraryIcon.Cooking -> R.string.icon_cooking
            LibraryIcon.Holidays -> R.string.icon_holidays
            LibraryIcon.Horror -> R.string.icon_horror
            LibraryIcon.SciFi -> R.string.icon_scifi
            LibraryIcon.Action -> R.string.icon_action
            LibraryIcon.Romance -> R.string.icon_romance
            LibraryIcon.Classics -> R.string.icon_classics
            LibraryIcon.World -> R.string.icon_world
            LibraryIcon.Uhd -> R.string.icon_uhd
            LibraryIcon.Favorites -> R.string.icon_favorites
            LibraryIcon.Folder -> R.string.icon_folder
        },
    )

// Apparence.

@Composable
private fun appearanceItems(
    state: SettingsState,
    actions: SettingsActions,
    systemReducedMotion: Boolean,
): List<SettingItem> {
    val appearance = state.appearance
    return listOf(
        deviceSection(),
        choice(
            "theme",
            stringResource(R.string.appearance_theme),
            stringResource(R.string.appearance_theme_description),
            ThemeChoice.entries,
            appearance.theme,
            ThemeChoice.Dark,
            { stringResource(if (it == ThemeChoice.Dark) R.string.theme_dark else R.string.theme_oled) },
        ) { value -> actions.updateAppearance { it.copy(theme = value) } },
        toggle(
            "reduced-motion",
            stringResource(R.string.appearance_motion),
            if (systemReducedMotion) {
                stringResource(R.string.appearance_motion_system)
            } else {
                stringResource(R.string.appearance_motion_description)
            },
            appearance.reducedMotion || systemReducedMotion,
            false,
        ) { actions.updateAppearance { it.copy(reducedMotion = !it.reducedMotion) } },
        choice(
            "text-size",
            stringResource(R.string.appearance_text),
            stringResource(R.string.appearance_text_description),
            TextSize.entries,
            appearance.textSize,
            TextSize.Normal,
            {
                stringResource(
                    when (it) {
                        TextSize.Normal -> R.string.text_normal
                        TextSize.Large -> R.string.text_large
                        TextSize.Larger -> R.string.text_larger
                    },
                )
            },
        ) { value -> actions.updateAppearance { it.copy(textSize = value) } },
        choice(
            "interface-size",
            stringResource(R.string.appearance_interface),
            stringResource(R.string.appearance_interface_description),
            InterfaceSize.entries,
            appearance.interfaceSize,
            InterfaceSize.VeryCompact,
            {
                stringResource(
                    when (it) {
                        InterfaceSize.VeryCompact -> R.string.interface_very_compact
                        InterfaceSize.Compact -> R.string.interface_compact
                        InterfaceSize.Normal -> R.string.interface_normal
                    },
                )
            },
        ) { value -> actions.updateAppearance { it.copy(interfaceSize = value) } },
    )
}

// Profil.

@Composable
private fun profileItems(
    state: SettingsState,
    actions: SettingsActions,
    navigation: SettingsNavigation,
): List<SettingItem> {
    val profile = state.session?.profile ?: return emptyList()
    return buildList {
        add(profileSection(state))
        add(
            SettingItem.Action(
                key = "pin",
                title = stringResource(if (profile.hasPin) R.string.pin_change else R.string.pin_set),
                description = stringResource(R.string.pin_description),
                value = stringResource(if (profile.hasPin) R.string.pin_on else R.string.pin_off),
                icon = CineIcons.Lock,
                onClick = { navigation.pin(profile, remove = false) },
            ),
        )
        if (profile.hasPin) {
            add(
                SettingItem.Action(key = "pin-remove", title = stringResource(R.string.pin_remove), onClick = {
                    navigation.pin(profile, remove = true)
                }),
            )
        }
        add(
            SettingItem.Action(
                key = "switch-profile",
                title = stringResource(R.string.switch_profile),
                icon = CineIcons.Profile,
                onClick = actions::switchProfile,
            ),
        )
        add(
            SettingItem.Action(
                key = "sign-out",
                title = stringResource(R.string.sign_out),
                description = stringResource(R.string.sign_out_description),
                onClick = actions::signOut,
            ),
        )
        add(deviceSection())
        val modes = listOf(StartupMode.AlwaysAsk, StartupMode.LastProfile)
        add(
            choice(
                "startup",
                stringResource(R.string.startup_title),
                stringResource(R.string.startup_description),
                modes,
                state.startupMode,
                StartupMode.AlwaysAsk,
                { stringResource(if (it == StartupMode.LastProfile) R.string.startup_last else R.string.startup_ask) },
            ) { value -> if (value != state.startupMode) actions.toggleStartupMode() },
        )
    }
}

// Réseau.

@Composable
private fun networkItems(
    state: SettingsState,
    actions: SettingsActions,
    callbacks: PageCallbacks,
): List<SettingItem> {
    val server = state.session?.connection?.server ?: return emptyList()
    return listOf(
        deviceSection(),
        SettingItem.Action(
            key = "address-local",
            title = stringResource(R.string.address_local),
            description = server.localAddress.url,
            icon = CineIcons.Server,
            onClick = { callbacks.navigation.address(remote = false) },
        ),
        SettingItem.Action(
            key = "address-remote",
            title = stringResource(R.string.address_remote),
            description = server.remoteAddress?.url ?: stringResource(R.string.address_remote_none),
            icon = CineIcons.Remote,
            onClick = { callbacks.navigation.address(remote = true) },
        ),
        choice(
            "network-mode",
            stringResource(R.string.network_mode),
            stringResource(R.string.network_mode_description),
            NetworkMode.entries,
            state.network,
            NetworkMode.Automatic,
            {
                stringResource(
                    when (it) {
                        NetworkMode.Automatic -> R.string.network_auto
                        NetworkMode.LocalOnly -> R.string.network_local
                        NetworkMode.RemoteOnly -> R.string.network_remote
                    },
                )
            },
        ) { value -> actions.setNetworkMode(value) },
        SettingItem.Section(
            key = "server",
            title = stringResource(R.string.section_server, server.name, server.version),
        ),
        SettingItem.Action(
            key = "forget-server",
            title = stringResource(R.string.forget_server),
            description = stringResource(R.string.forget_server_description),
            destructive = true,
            onClick = callbacks.onForget,
        ),
    )
}

// Appareil.

@Composable
private fun deviceItems(
    state: SettingsState,
    navigation: SettingsNavigation,
): List<SettingItem> =
    listOf(
        deviceSection(),
        SettingItem.Action(
            key = "device-name",
            title = stringResource(R.string.device_name),
            description = described(stringResource(R.string.device_name_description), state.tvName),
            value = state.deviceName ?: stringResource(R.string.device_name_tv, state.tvName),
            icon = CineIcons.Edit,
            onClick = navigation::deviceName,
        ),
        SettingItem.Action(
            key = "update",
            title = stringResource(R.string.update_row),
            description = state.updateText(),
            value = state.update?.versionName?.let { stringResource(R.string.update_version, it) },
            icon = CineIcons.Retry,
            onClick = navigation::checkUpdate,
        ),
        SettingItem.Action(
            key = "about",
            title = stringResource(R.string.about),
            description = stringResource(R.string.about_description),
            icon = CineIcons.Info,
            onClick = {},
        ),
    )

@Composable
private fun SettingsState.updateText(): String {
    val update = update ?: return stringResource(R.string.update_check)
    val phase = update.phase
    val checked = update.lastCheck?.atZone(ZoneId.systemDefault())
    return when {
        AppVersion.parse(update.versionName) == null -> {
            stringResource(R.string.update_development)
        }

        phase is UpdatePhase.Available -> {
            stringResource(R.string.update_available, phase.release.version.name)
        }

        checked != null -> {
            stringResource(
                R.string.update_checked,
                checked.toLocalDate().toDateText(),
                checked.toLocalTime().toTimeText(),
            )
        }

        else -> {
            stringResource(R.string.update_check)
        }
    }
}

// Avancé.

@Composable
private fun advancedItems(
    actions: SettingsActions,
    callbacks: PageCallbacks,
): List<SettingItem> {
    val navigation = callbacks.navigation
    return buildList {
        add(deviceSection())
        add(
            SettingItem.Action(
                key = "logs",
                title = stringResource(R.string.advanced_logs),
                description =
                    stringResource(
                        R.string.advanced_logs_description,
                        fr.guim.cinedoury.core.source.FailureLog.CAPACITY,
                    ),
                icon = CineIcons.History,
                onClick = navigation::logs,
            ),
        )
        add(
            SettingItem.Action(
                key = "report",
                title = stringResource(R.string.advanced_report),
                description = stringResource(R.string.advanced_report_description),
                icon = CineIcons.FileInfo,
                onClick = navigation::report,
            ),
        )
        add(
            SettingItem.Action(
                key = "clear-cache",
                title = stringResource(R.string.advanced_cache),
                description = stringResource(R.string.advanced_cache_description),
                icon = CineIcons.Delete,
                onClick = { actions.clearCache(navigation::clearImageCache) },
            ),
        )
        if (callbacks.showDebug) {
            add(SettingItem.Section(key = "debug", title = stringResource(R.string.section_debug)))
            add(
                SettingItem.Action(
                    key = "catalog",
                    title = stringResource(R.string.catalog),
                    description = stringResource(R.string.catalog_description),
                    onClick = navigation::catalog,
                ),
            )
            add(
                SettingItem.Action(
                    key = "device-profile",
                    title = stringResource(R.string.device_profile),
                    description = stringResource(R.string.device_profile_description),
                    onClick = navigation::deviceProfile,
                ),
            )
        }
    }
}

private const val PERCENT = 100
private const val MEGABIT = 1_000_000
