package fr.guim.cinedoury.core.data.server

import fr.guim.cinedoury.core.source.ServerAddress
import java.net.URI
import java.net.URISyntaxException

/**
 * Turns what was typed with the remote into a server address (`SPEC.md` § 4.7): `http://` when no
 * scheme is given, and Jellyfin's port 8096 for `http` without a port. `192.0.2.10` gives
 * `http://192.0.2.10:8096`; `https://example.com/jellyfin` stays as it is.
 */
object AddressInput {
    const val DEFAULT_PORT = 8096
    private val BARE_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:(?!//)(\\D|$)")

    fun parse(input: String): ServerAddress? {
        val trimmed = input.trim().trimEnd('/')
        // « http: » without a host is a scheme, not a host followed by a port.
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() } || BARE_SCHEME.containsMatchIn(trimmed)) return null
        val uri =
            try {
                URI(if ("://" in trimmed) trimmed else "http://$trimmed")
            } catch (_: URISyntaxException) {
                null
            }
        val scheme = uri?.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
        val host = uri?.host
        if (uri == null || scheme == null || host.isNullOrEmpty()) return null
        val port =
            when {
                uri.port != -1 -> ":${uri.port}"
                scheme == "http" -> ":$DEFAULT_PORT"
                else -> ""
            }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return ServerAddress.of("$scheme://$host$port$path")
    }
}
