package dev.thinkami.podcastplayer.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.thinkami.podcastplayer.appContainer
import dev.thinkami.podcastplayer.data.EpisodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * バックグラウンド再生の器。
 *
 * 通知・ロック画面の操作、Bluetoothのボタン、着信時の一時停止(オーディオフォーカス)は Media3 が面倒を見る。ここが自前で持つのは「再生位置をDBに書き戻す」
 * 「聴き終わったエピソードを視聴済みにする」「キューが尽きたらプレイヤーを空に戻す」の3つだけ。
 *
 * 「聴き終わった」の判断は位置の計算ではなくプレイヤーのイベント(自動遷移・再生終了)で行う。 実際に鳴り終わったときにしか発火しないため、そのファイルはもう掴まれておらず、削除を遅らせる
 * 必要がない。
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

    /**
     * いま鳴っているエピソード。曲が変わった瞬間に「前のもの」を知るために保持する。
     *
     * 書き手は [PlaybackListener.onMediaItemTransition] ただ1つ。切り替えは再生開始・自動遷移・
     * シーク・キューのクリアのいずれでも必ずそこを通るため、他所から補正する必要がない。
     */
    private var currentEpisodeId: Long? = null

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
     * 再生位置を定期的に保存する。
     *
     * 位置には「変わった瞬間」の通知がない(再生中は連続的に進む)ため、ここだけはポーリングで 覗きに行くしかない。逆に「聴き終わったか」はイベントで分かるので、この経路では判断しない。
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
        episodeRepository.savePosition(episodeId, player.currentPosition)
    }

    private inner class PlaybackListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = currentEpisodeId
            currentEpisodeId = mediaItem?.mediaId?.toLongOrNull()

            // 自動で次へ移ったということは、前のエピソードは最後まで鳴り切ったということ。
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previous != null) {
                completeAndDelete(previous)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return

            // キューの最後まで鳴り切った。
            val finished = currentEpisodeId
            if (finished != null) {
                completeAndDelete(finished)
            }
            // 聴き終わったらプレイヤーを空に戻す。currentMediaItem が null になることで、
            // 通知もミニプレイヤーも消え、行のアイコンはDL済みかどうかだけで決まる状態に戻る。
            // ファイル削除は上でコルーチンに預けてあるため、この停止より後に走る。
            mediaSession?.player?.run {
                stop()
                clearMediaItems()
            }
        }
    }

    /** 聴き終わったことが確定した。視聴済みにし、条件を満たせばファイルを消す。 */
    private fun completeAndDelete(episodeId: Long) {
        serviceScope.launch { episodeRepository.markPlaybackCompleted(episodeId) }
    }

    private companion object {
        const val SKIP_MS = 10_000L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
    }
}
