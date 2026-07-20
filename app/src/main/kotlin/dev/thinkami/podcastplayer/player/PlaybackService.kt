package dev.thinkami.podcastplayer.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.thinkami.podcastplayer.appContainer
import dev.thinkami.podcastplayer.data.EpisodeRepository
import dev.thinkami.podcastplayer.logic.ListeningRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * バックグラウンド再生の器。
 *
 * 通知・ロック画面の操作、Bluetoothのボタン、着信時の一時停止(オーディオフォーカス)は Media3 が面倒を見る。ここが自前で持つのは「再生位置をDBに書き戻す」ことと
 * 「聴き終わったエピソードを視聴済みにする」ことだけ。
 *
 * 再生順(未DLをスキップして次へ)はプレイリストとして事前に組み立てて渡される。 このサービスが自分でダウンロードを始めることは絶対にない。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * メインスレッドに固定する。ExoPlayer は生成したスレッド(=メイン)からしか触れず、 別スレッドから読むと `Player is accessed on the wrong
     * thread` で落ちる。 ここから呼ぶリポジトリ側は、必要な入出力を自分で IO ディスパッチャへ逃がす。
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 視聴済みにしたが、まだ再生中でファイルを消せていないエピソード。 */
    private val pendingDeletion = mutableSetOf<Long>()

    private val episodeRepository: EpisodeRepository
        get() = appContainer.episodeRepository

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer.Builder(this)
                // 着信時の一時停止と再開はここで有効になる。
                .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
                // イヤホンを抜いたら止める。
                .setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(SKIP_MS)
                .setSeekForwardIncrementMs(SKIP_MS)
                .build()
        player.addListener(PlaybackListener())
        mediaSession = MediaSession.Builder(this, player).build()
        startPositionTracking(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // アプリをタスク一覧から消しても、再生中なら鳴らし続ける。
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * 再生位置を定期的に保存し、残り時間が閾値を切ったら視聴済みにする。
     *
     * 「最後まで再生したか」をファイルの終端ではなく位置で判断するため、エンディングの途中で 止めても視聴済みになる。判断そのものは logic 層の [ListeningRules]
     * が持つ。
     */
    private fun startPositionTracking(player: Player) {
        serviceScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) {
                    persistProgress(player)
                }
            }
        }
    }

    private suspend fun persistProgress(player: Player) {
        val episodeId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET }

        episodeRepository.savePosition(episodeId, position)

        if (
            ListeningRules.isPlaybackComplete(position, duration) && episodeId !in pendingDeletion
        ) {
            // 視聴済みにするのは即座に(フィルターに反映させるため)。
            // ファイル削除は再生中のファイルを掴んだまま消さないよう、この曲から離れてから行う。
            episodeRepository.setPlayed(episodeId, played = true)
            pendingDeletion += episodeId
        }
    }

    private inner class PlaybackListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 別のエピソードへ移ったので、聴き終わったぶんのファイルを消してよい。
            flushPendingDeletion(exceptMediaId = mediaItem?.mediaId?.toLongOrNull())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                flushPendingDeletion(exceptMediaId = null)
            }
        }
    }

    private fun flushPendingDeletion(exceptMediaId: Long?) {
        val targets = pendingDeletion.filterNot { it == exceptMediaId }
        if (targets.isEmpty()) return
        pendingDeletion.removeAll(targets.toSet())
        serviceScope.launch { episodeRepository.deleteDownloadsIfEligible(targets) }
    }

    private companion object {
        const val SKIP_MS = 10_000L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
    }
}
