package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.db.ProfileDao
import fr.guim.cinedoury.core.data.db.ProfileEntity
import fr.guim.cinedoury.core.data.security.PinHasher
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.model.UserId
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The profiles of each server on this device (`SPEC.md` § 4.7): the public users of the server,
 * and every user who signed in here, each with their own token. Several profiles stay signed in
 * side by side.
 */
class ProfileRepository(
    private val dao: ProfileDao,
    private val cipher: TokenCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(serverId: String): Flow<List<Profile>> = dao.observe(serverId).map { list -> list.map { it.toModel() } }

    suspend fun get(
        serverId: String,
        userId: String,
    ): Profile? = dao.get(serverId, userId)?.toModel()

    /**
     * Brings the public users of the server up to date: new ones appear, names and pictures follow
     * the server, and a profile that left the public list goes, unless it is signed in or has a PIN
     * here.
     */
    suspend fun rememberPublicUsers(
        serverId: String,
        users: List<User>,
    ) {
        val existing = dao.all(serverId).associateBy { it.userId }
        users.forEach { user ->
            val known = existing[user.id.value]
            dao.upsert(
                (known ?: newEntity(serverId, user)).copy(
                    name = user.name,
                    isAdministrator = user.isAdministrator,
                    imageTag = user.image?.tag,
                    isPublic = true,
                ),
            )
        }
        val publicIds = users.map { it.id.value }.toSet()
        existing.values
            .filter { it.userId !in publicIds }
            .forEach { profile ->
                if (profile.encryptedToken == null && profile.pinHash == null) {
                    dao.delete(serverId, profile.userId)
                } else if (profile.isPublic) {
                    dao.upsert(profile.copy(isPublic = false))
                }
            }
    }

    /** Keeps the token of [session], encrypted, and marks the profile as just used. */
    suspend fun saveSession(session: Session): Profile {
        val known = dao.get(session.serverId, session.user.id.value)
        val entity =
            (known ?: newEntity(session.serverId, session.user, isPublic = false)).copy(
                name = session.user.name,
                isAdministrator = session.user.isAdministrator,
                imageTag = session.user.image?.tag ?: known?.imageTag,
                // The Keystore may take a while on a slow device (key creation): never the main thread.
                encryptedToken = withContext(Dispatchers.Default) { cipher.encrypt(session.accessToken) },
                lastUsedAt = clock(),
            )
        dao.upsert(entity)
        return entity.toModel()
    }

    /**
     * The session of a signed-in profile, reached at [address]; null when the profile is signed out
     * or its token cannot be read any more (then it is signed out).
     */
    suspend fun session(
        profile: Profile,
        address: ServerAddress,
    ): Session? {
        val entity = dao.get(profile.serverId, profile.user.id.value)
        val encrypted = entity?.encryptedToken ?: return null
        val token = withContext(Dispatchers.Default) { cipher.decrypt(encrypted) }
        // An unreadable token (Keystore key lost) signs the profile out.
        if (token == null) FailureLog.warn("Profile ${profile.user.id.value}: token unreadable, signed out")
        dao.upsert(if (token == null) entity.copy(encryptedToken = null) else entity.copy(lastUsedAt = clock()))
        return token?.let {
            Session(server = address, serverId = profile.serverId, user = entity.toUser(), accessToken = it)
        }
    }

    suspend fun signOut(profile: Profile) {
        update(profile) { it.copy(encryptedToken = null) }
    }

    /** Sets the PIN of [profile], or removes it with null. */
    suspend fun setPin(
        profile: Profile,
        pin: String?,
    ) {
        // PBKDF2 takes a few tens of milliseconds: never on the main thread.
        val hash = pin?.let { withContext(Dispatchers.Default) { PinHasher.hash(it) } }
        update(profile) { it.copy(pinHash = hash) }
    }

    suspend fun checkPin(
        profile: Profile,
        pin: String,
    ): Boolean {
        val hash = dao.get(profile.serverId, profile.user.id.value)?.pinHash ?: return true
        return withContext(Dispatchers.Default) { PinHasher.verify(pin, hash) }
    }

    suspend fun deleteAll(serverId: String) {
        dao.deleteAll(serverId)
    }

    private suspend fun update(
        profile: Profile,
        change: (ProfileEntity) -> ProfileEntity,
    ) {
        val entity = dao.get(profile.serverId, profile.user.id.value) ?: return
        dao.upsert(change(entity))
    }

    private fun newEntity(
        serverId: String,
        user: User,
        isPublic: Boolean = true,
    ) = ProfileEntity(
        serverId = serverId,
        userId = user.id.value,
        name = user.name,
        isAdministrator = user.isAdministrator,
        imageTag = user.image?.tag,
        isPublic = isPublic,
        encryptedToken = null,
        pinHash = null,
        lastUsedAt = null,
    )

    private fun ProfileEntity.toUser() =
        User(
            id = UserId(userId),
            name = name,
            isAdministrator = isAdministrator,
            // Jellyfin 12.3 reports a password for every user (CLAUDE.md, « Pièges »).
            hasPassword = true,
            image = imageTag?.let { ImageRef(ItemId(userId), ImageType.Primary, it) },
        )

    private fun ProfileEntity.toModel() =
        Profile(
            serverId = serverId,
            user = toUser(),
            isSignedIn = encryptedToken != null,
            hasPin = pinHash != null,
        )
}
