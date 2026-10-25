package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.db.ServerDao
import fr.guim.cinedoury.core.data.db.ServerEntity
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.source.ServerAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The servers remembered on this device. */
class ServerRepository(
    private val dao: ServerDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun get(id: String): SavedServer? = dao.get(id)?.toModel()

    fun observe(id: String): Flow<SavedServer?> = dao.observe(id).map { it?.toModel() }

    /**
     * Remembers [info], reached at [address], as the local address. A server already known keeps
     * its remote address.
     */
    suspend fun save(
        info: ServerInfo,
        address: ServerAddress,
    ): SavedServer {
        val existing = dao.get(info.id)
        val entity =
            ServerEntity(
                id = info.id,
                name = info.name,
                version = info.version,
                localAddress = address.url,
                remoteAddress = existing?.remoteAddress,
                addedAt = existing?.addedAt ?: clock(),
            )
        dao.upsert(entity)
        return entity.toModel()
    }

    suspend fun setAddresses(
        id: String,
        local: ServerAddress,
        remote: ServerAddress?,
    ) {
        val existing = dao.get(id) ?: return
        dao.upsert(existing.copy(localAddress = local.url, remoteAddress = remote?.url))
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun ServerEntity.toModel() =
        SavedServer(
            id = id,
            name = name,
            version = version,
            localAddress = ServerAddress.of(localAddress),
            remoteAddress = remoteAddress?.let(ServerAddress::of),
        )
}
