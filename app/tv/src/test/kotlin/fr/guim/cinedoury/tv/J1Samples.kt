package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.Connection
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SavedServer
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.model.UserId
import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.tv.catalog.SAMPLE_HASH_ORANGE
import fr.guim.cinedoury.tv.catalog.SAMPLE_HASH_TEAL

/** Sample content of the J1 screenshots: library data, the same in every language. */
internal object J1Samples {
    val local = ServerAddress.of("http://192.0.2.10:8096")
    val remote = ServerAddress.of("https://example.com")

    val servers =
        listOf(
            DiscoveredServer("home", "Maison", local),
            DiscoveredServer("cabin", "Chalet", ServerAddress.of("http://192.0.2.24:8096")),
        )

    val server = SavedServer("home", "Maison", "12.3.0", local, remote)

    private fun user(
        id: String,
        name: String,
    ) = User(UserId(id), name, isAdministrator = name == "Camille", hasPassword = true)

    val profiles =
        listOf(
            Profile("home", user("1", "Camille"), isSignedIn = true, hasPin = true),
            Profile("home", user("2", "Léo"), isSignedIn = true, hasPin = false),
            Profile("home", user("3", "enfant"), isSignedIn = false, hasPin = false),
        )

    val libraries =
        listOf(
            Library(
                LibraryId("films"),
                "Films",
                LibraryKind.Movies,
                ImageRef(ItemId("films"), ImageType.Primary, "t", blurHash = SAMPLE_HASH_ORANGE),
            ),
            Library(
                LibraryId("series"),
                "Séries",
                LibraryKind.Shows,
                ImageRef(ItemId("series"), ImageType.Primary, "t", blurHash = SAMPLE_HASH_TEAL),
            ),
            Library(LibraryId("sagas"), "Le Phare : la saga", LibraryKind.Collections),
        )

    fun session(
        mode: ConnectionMode = ConnectionMode.Local,
        profile: Profile = profiles.first(),
    ): ActiveSession {
        val source = InMemorySource()
        val session = Session(local, "home", profile.user, "token")
        return ActiveSession(
            Connection(
                server,
                if (mode ==
                    ConnectionMode.Local
                ) {
                    local
                } else {
                    remote
                },
                mode,
            ),
            profile,
            session,
            source.open(session),
        )
    }
}
