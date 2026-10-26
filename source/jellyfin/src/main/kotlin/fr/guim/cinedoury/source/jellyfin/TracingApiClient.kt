package fr.guim.cinedoury.source.jellyfin

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The last request of a [jellyfinCall], for the detail of its failure: method and path template
 * (`/Items/{itemId}`), never the query (where a secret could travel) nor the body.
 */
internal class RequestTrace : AbstractCoroutineContextElement(Key) {
    @Volatile
    var request: String? = null

    @Volatile
    var status: Int? = null

    companion object Key : CoroutineContext.Key<RequestTrace>
}

/**
 * An [ApiClient] that runs every request on [io] and records it in the [RequestTrace] of the call.
 *
 * The SDK's OkHttp client reads the body of the answer in the coroutine that called it, after
 * `enqueue`: called from the main thread (a view model), Android throws
 * `NetworkOnMainThreadException` as soon as the body does not fit in the first packet
 * (CLAUDE.md, « Pièges »).
 */
internal class TracingApiClient(
    private val delegate: ApiClient,
    private val io: CoroutineDispatcher,
    /** The device as it introduces itself now: its name may change in the settings (J7). */
    private val device: () -> DeviceInfo = { delegate.deviceInfo },
) : ApiClient() {
    override val baseUrl: String? get() = delegate.baseUrl
    override val accessToken: String? get() = delegate.accessToken
    override val clientInfo: ClientInfo get() = delegate.clientInfo
    override val deviceInfo: DeviceInfo get() = device()
    override val httpClientOptions: HttpClientOptions get() = delegate.httpClientOptions
    override val webSocket: SocketApi get() = delegate.webSocket

    override fun update(
        baseUrl: String?,
        accessToken: String?,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ) = delegate.update(baseUrl, accessToken, clientInfo, deviceInfo)

    override fun createUrl(
        pathTemplate: String,
        pathParameters: Map<String, Any?>,
        queryParameters: Map<String, Any?>,
        ignorePathParameters: Boolean,
    ): String = delegate.createUrl(pathTemplate, pathParameters, queryParameters, ignorePathParameters)

    override suspend fun request(
        method: HttpMethod,
        pathTemplate: String,
        pathParameters: Map<String, Any?>,
        queryParameters: Map<String, Any?>,
        requestBody: Any?,
    ): RawResponse {
        val current = device()
        if (delegate.deviceInfo != current) {
            delegate.update(delegate.baseUrl, delegate.accessToken, delegate.clientInfo, current)
        }
        val trace = currentCoroutineContext()[RequestTrace]
        trace?.request = "$method $pathTemplate"
        trace?.status = null
        return try {
            withContext(io) { delegate.request(method, pathTemplate, pathParameters, queryParameters, requestBody) }
                .also { trace?.status = it.status }
        } catch (error: InvalidStatusException) {
            trace?.status = error.status
            throw error
        }
    }
}
