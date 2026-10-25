package fr.guim.cinedoury.core.model

@JvmInline
value class UserId(val value: String)

data class User(
    val id: UserId,
    val name: String,
    val isAdministrator: Boolean,
    val hasPassword: Boolean,
    val image: ImageRef? = null,
)

/** A server as identified by its public information, before any sign-in. */
data class ServerInfo(
    val id: String,
    val name: String,
    val version: String,
)
