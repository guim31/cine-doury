package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineFocus
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.source.SourceException

/** An error in two sentences (`UI.md` § 10): what happened, what to do; the detail apart. */
data class ErrorText(
    val title: String,
    val message: String,
    val details: String,
)

/** The readable message of a source failure, in the language of the device. */
@Composable
fun SourceException.toErrorText(): ErrorText {
    val (title, message) =
        when (this) {
            is SourceException.Unreachable -> {
                stringResource(R.string.error_unreachable_title) to stringResource(R.string.error_unreachable_message)
            }

            is SourceException.NotAServer -> {
                stringResource(R.string.error_not_a_server_title) to stringResource(R.string.error_not_a_server_message)
            }

            is SourceException.Incompatible -> {
                stringResource(R.string.error_incompatible_title) to
                    stringResource(R.string.error_incompatible_message, serverVersion, minimumVersion)
            }

            is SourceException.InvalidCredentials -> {
                stringResource(R.string.error_credentials_title) to stringResource(R.string.error_credentials_message)
            }

            is SourceException.QuickConnectExpired -> {
                stringResource(R.string.error_code_expired_title) to stringResource(R.string.error_code_expired_message)
            }

            is SourceException.QuickConnectDisabled -> {
                stringResource(R.string.error_quick_connect_off_title) to
                    stringResource(R.string.error_quick_connect_off_message)
            }

            is SourceException.Unauthorized -> {
                stringResource(R.string.error_session_title) to stringResource(R.string.error_session_message)
            }

            is SourceException.Forbidden -> {
                stringResource(R.string.error_forbidden_title) to stringResource(R.string.error_forbidden_message)
            }

            is SourceException.Server -> {
                stringResource(R.string.error_server_title) to stringResource(R.string.error_server_message)
            }

            else -> {
                stringResource(R.string.error_unexpected_title) to stringResource(R.string.error_unexpected_message)
            }
        }
    return ErrorText(title, message, details())
}

/**
 * The technical detail of « Plus d'infos »: the failure, the request (method and path, never a
 * token or a password: `SourceException.request`), the HTTP status and each cause with its class.
 */
@Composable
private fun SourceException.details(): String {
    val request = request?.let { stringResource(R.string.error_detail_request, it) }
    val status = httpStatus?.let { stringResource(R.string.error_detail_status, it) }
    val causes =
        generateSequence(cause) { it.cause?.takeIf { next -> next !== it } }
            .take(MAX_CAUSES)
            .map { "${it.javaClass.name}: ${it.message.orEmpty()}" }
            .toList()
    val causeLines = causes.map { stringResource(R.string.error_detail_cause, it) }
    val failure = "SourceException.${this::class.simpleName}: ${message.orEmpty()}"
    return listOfNotNull(failure, request, status).plus(causeLines).joinToString("\n")
}

private const val MAX_CAUSES = 4

/**
 * A source failure as a [MessagePanel]: « Réessayer » with the focus, an optional second way out,
 * and the technical detail behind « Plus d'infos ».
 */
@Composable
fun SourceErrorPanel(
    error: SourceException,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(R.string.action_retry),
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    retryFocusRequester: FocusRequester? = null,
    retryFocus: CineFocus = rememberCineFocus(PREVIEW_RETRY),
    showDetails: Boolean = false,
) {
    val text = error.toErrorText()
    MessagePanel(
        title = text.title,
        message = text.message,
        actionLabel = retryLabel,
        onAction = onRetry,
        modifier = modifier,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        detailsLabel = stringResource(R.string.action_more_info),
        details = text.details,
        showDetails = showDetails,
        actionFocusRequester = retryFocusRequester,
        actionFocus = retryFocus,
        detailsFocus = rememberCineFocus(PREVIEW_DETAILS),
    )
}

/** Preview tag of the « Réessayer » button of a [SourceErrorPanel]. */
const val PREVIEW_RETRY = "retry"

/** Preview tag of the detail of a [SourceErrorPanel], once « Plus d'infos » is open. */
const val PREVIEW_DETAILS = "details"
