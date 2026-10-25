package fr.guim.cinedoury.core.data.index

import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the local index up to date in the background (`SPEC.md` § 4.10): once a profile is open (a
 * few seconds later, so that the home loads first), then every few hours while the app runs. Never on
 * the main thread; a failure waits for the next turn.
 */
class IndexScheduler(
    private val sessions: SessionManager,
    private val index: LibraryIndex,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val startDelay: Duration = START_DELAY,
) {
    private var started: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started != null) return
        started =
            scope.launch {
                sessions.active
                    .distinctUntilChanged { a, b -> a?.session == b?.session }
                    .collectLatest { active ->
                        if (active == null) return@collectLatest
                        delay(startDelay)
                        while (true) {
                            update(active, whole = false)
                            delay(EVERY)
                        }
                    }
            }
    }

    /** « Mettre à jour l'index » in the settings: reads everything again, now. */
    fun rebuild() {
        val active = sessions.active.value ?: return
        scope.launch { update(active, whole = true) }
    }

    private suspend fun update(
        active: ActiveSession,
        whole: Boolean,
    ) {
        try {
            index.sync(active.profile.serverId, active.library, active.library.libraries(), whole)
        } catch (_: SourceException) {
            // Shown in the settings by the status of the index; the next turn tries again.
        }
    }

    companion object {
        val START_DELAY: Duration = 5.seconds
        val EVERY: Duration = 6.hours
    }
}
