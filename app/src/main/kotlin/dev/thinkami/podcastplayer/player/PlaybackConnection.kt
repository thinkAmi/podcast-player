package dev.thinkami.podcastplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.thinkami.podcastplayer.logic.ListeningRules
import dev.thinkami.podcastplayer.logic.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** いま鳴っているものの状態。UI(ミニプレイヤー・プレイヤー画面)が読む。 */
data class PlaybackStatus(
    val episodeId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
)

/**
 * UI から [PlaybackService] を操作するための接続。
 *
 * Media3 の MediaController を通すことで、アプリのプロセスが再生サービスと分かれていても 同じ手順で操作できる。
 */
class PlaybackConnection(private val context: Context) {

    private var controller: MediaController? = null
    private val mutableStatus = MutableStateFlow(PlaybackStatus())
    val status: StateFlow<PlaybackStatus> = mutableStatus.asStateFlow()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = future.get().also { it.addListener(StatusListener()) }
                publishStatus()
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        controller?.release()
        controller = null
    }

    /**
     * 再生順を渡して先頭から再生する。
     *
     * [order] は logic 層の PlaybackQueue が組み立てたもので、DL済みのものしか含まない。
     */
    fun play(order: List<Episode>, speed: Float) {
        val player = controller ?: return
        if (order.isEmpty()) return

        player.setMediaItems(order.map { it.toMediaItem() })
        player.prepare()
        player.setPlaybackSpeed(speed)
        // 途中まで聴いていたなら続きから。聴き終わっているものは先頭から聴き直せる。
        player.seekTo(0, ListeningRules.resumePositionMs(order.first()))
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBack() {
        controller?.seekBack()
    }

    fun seekForward() {
        controller?.seekForward()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        publishStatus()
    }

    /** シークバーの表示更新のため、UI 側から定期的に呼ぶ。 */
    fun refreshStatus() {
        publishStatus()
    }

    private fun publishStatus() {
        // 切断中(回転などによる release → connect の谷間)は最後の状態を保持し、何も流さない。
        // ここで空の status を流すと、UI の定期更新経由で episodeId=null が「キュー終端」と
        // 区別できなくなり、開いていたプレイヤー画面が誤って閉じる。episodeId=null が流れるのは
        // 接続済みプレイヤーのキューが空のときだけ、という不変条件を保つ。
        val player = controller ?: return
        mutableStatus.value =
            PlaybackStatus(
                episodeId = player.currentMediaItem?.mediaId?.toLongOrNull(),
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                speed = player.playbackParameters.speed,
            )
    }

    private inner class StatusListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishStatus()
        }
    }
}

private fun Episode.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(localPath?.let { path -> Uri.fromFile(java.io.File(path)) } ?: "".toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
