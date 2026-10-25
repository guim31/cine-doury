package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-off events of a view model (« signed in », « server forgotten »), each delivered once to
 * the screen that is shown, unlike a state that would be replayed when coming back to it.
 */
class Events<T> {
    private val channel = Channel<T>(Channel.BUFFERED)

    val flow: Flow<T> = channel.receiveAsFlow()

    fun send(event: T) {
        channel.trySend(event)
    }
}

/** Collects [events] while the screen is shown. */
@Composable
fun <T> CollectEvents(
    events: Events<T>,
    onEvent: (T) -> Unit,
) {
    val handler by rememberUpdatedState(onEvent)
    LaunchedEffect(events) { events.flow.collect { handler(it) } }
}
