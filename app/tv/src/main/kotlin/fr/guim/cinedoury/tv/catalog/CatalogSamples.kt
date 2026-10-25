package fr.guim.cinedoury.tv.catalog

/**
 * Sample content shown by the catalogue. These are library data (titles and years), not interface
 * text: they come from the test library of `ci/jellyfin` and stay the same in every language.
 */
internal data class SamplePoster(
    val title: String,
    val year: Int,
    val progress: Float? = null,
    val unwatched: Boolean = false,
    val unwatchedCount: Int? = null,
)

internal val SamplePosters =
    listOf(
        SamplePoster("Le Phare", 2001, unwatched = true),
        SamplePoster("Retour au phare", 2004, progress = 0.42f),
        SamplePoster("Nuit polaire", 2020, unwatched = true),
        SamplePoster("Les Marées", 2015, unwatchedCount = 3),
        SamplePoster("Une saison", 2018),
        SamplePoster("Double jeu", 2012, progress = 0.8f),
        SamplePoster("Le Phare : la saga", 2001),
    )

internal const val SAMPLE_SERIES = "Les Marées"
internal const val SAMPLE_FILM = "Nuit polaire"
internal const val SAMPLE_SINGLE_SEASON = "Une saison"
