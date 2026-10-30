package fr.guim.cinedoury.feature.player.mobile

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import fr.guim.cinedoury.feature.player.R

/**
 * The picture in picture of the phone (M2, `SPEC.md` § 4.27): leaving the app while the film plays
 * (the home gesture) shrinks it into a small window, with « −10 s », play or pause, « +10 s ». Paused,
 * leaving the app leaves it (and the film stays paused). Returns whether the window is small now.
 */
@Composable
fun rememberPictureInPicture(
    activity: ComponentActivity?,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onJump: (forward: Boolean) -> Unit,
): State<Boolean> {
    val inPicture = remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    val supported =
        remember(activity) {
            activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
        }
    val playPause by rememberUpdatedState(onPlayPause)
    val jump by rememberUpdatedState(onJump)
    val isPlaying by rememberUpdatedState(playing)
    if (activity == null || !supported) return inPicture
    DisposableEffect(activity, playing) {
        val params = params(activity, playing)
        runCatching { activity.setPictureInPictureParams(params) }
        val leave =
            Runnable {
                // Android 12 enters by itself (setAutoEnterEnabled); Android 11 is asked here.
                if (isPlaying && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    runCatching { activity.enterPictureInPictureMode(params) }
                }
            }
        activity.addOnUserLeaveHintListener(leave)
        onDispose { activity.removeOnUserLeaveHintListener(leave) }
    }
    DisposableEffect(activity) {
        val modes = Consumer<PictureInPictureModeChangedInfo> { inPicture.value = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(modes)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.getIntExtra(EXTRA, 0)) {
                        PLAY_PAUSE -> playPause()
                        BACK -> jump(false)
                        FORWARD -> jump(true)
                    }
                }
            }
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(modes)
            activity.unregisterReceiver(receiver)
            // The player closed: leaving the app no longer shrinks anything.
            runCatching { activity.setPictureInPictureParams(params(activity, playing = false, actions = false)) }
        }
    }
    return inPicture
}

private fun params(
    activity: ComponentActivity,
    playing: Boolean,
    actions: Boolean = true,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(WIDTH, HEIGHT))
    if (actions) {
        builder.setActions(
            listOf(
                action(activity, BACK, R.drawable.ic_pip_rewind, R.string.mobile_player_back_10),
                if (playing) {
                    action(activity, PLAY_PAUSE, R.drawable.ic_pip_pause, R.string.player_pause)
                } else {
                    action(activity, PLAY_PAUSE, R.drawable.ic_pip_play, R.string.player_play)
                },
                action(activity, FORWARD, R.drawable.ic_pip_forward, R.string.mobile_player_forward_10),
            ),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(playing && actions)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

private fun action(
    context: Context,
    code: Int,
    icon: Int,
    label: Int,
): RemoteAction {
    val intent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(ACTION).setPackage(context.packageName).putExtra(EXTRA, code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val text = context.getString(label)
    return RemoteAction(Icon.createWithResource(context, icon), text, text, intent)
}

private const val ACTION = "fr.guim.cinedoury.PICTURE_IN_PICTURE"
private const val EXTRA = "control"
private const val PLAY_PAUSE = 1
private const val BACK = 2
private const val FORWARD = 3
private const val WIDTH = 16
private const val HEIGHT = 9
