package dev.thinkami.podcastplayer.ui.episodes

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.EpisodeRepository
import dev.thinkami.podcastplayer.data.FeedRepository
import dev.thinkami.podcastplayer.data.artwork.ArtworkStore
import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.data.download.EpisodeDownloader
import dev.thinkami.podcastplayer.data.net.NetworkStateProvider
import dev.thinkami.podcastplayer.logic.PlaybackQueue
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.Feed
import dev.thinkami.podcastplayer.logic.model.PlayedSnapshot
import dev.thinkami.podcastplayer.player.PlaybackConnection
import dev.thinkami.podcastplayer.player.PlaybackStatus
import dev.thinkami.podcastplayer.ui.ArtworkSizes
import dev.thinkami.podcastplayer.ui.PlayedUndoHolder
import dev.thinkami.podcastplayer.ui.UndoablePlayedChange
import dev.thinkami.podcastplayer.ui.playedMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 統合エピソード画面の状態と操作。
 *
 * この画面は「表示中のエピソードの現況を映すだけ」の一枚岩で、モードを持たない。 鳴っているものに追随するかどうか(=画面の差し替え)は nav の仕事であり、ここは [isCurrent]
 * を伝えるところまでを受け持つ。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeDetailViewModel(
    private val episodeId: Long,
    private val episodeRepository: EpisodeRepository,
    private val feedRepository: FeedRepository,
    private val downloader: EpisodeDownloader,
    private val networkState: NetworkStateProvider,
    private val playback: PlaybackConnection,
    private val playedUndo: PlayedUndoHolder,
    artworkStore: ArtworkStore,
) : ViewModel() {

    val episode: StateFlow<Episode?> =
        episodeRepository
            .observeEpisode(episodeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** 表示中エピソードが属する番組。アートワーク・再生速度・再生順の絞り込み条件の持ち主。 */
    val feed: StateFlow<Feed?> =
        episode
            .map { it?.feedId }
            .distinctUntilChanged()
            .flatMapLatest { feedId ->
                if (feedId == null) flowOf(null) else feedRepository.observeFeed(feedId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** アートワークは番組の属性。再生していなくても表示する。 */
    val artwork: StateFlow<Bitmap?> =
        feed
            .map { it?.artworkLocalPath }
            .distinctUntilChanged()
            .map { path -> artworkStore.load(path, ArtworkSizes.PLAYER_TARGET_PX) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val status: StateFlow<PlaybackStatus> = playback.status

    /** このエピソードがいま鳴っているものかどうか。画面の見た目とミニプレイヤーの出し分けの根拠。 */
    val isCurrent: StateFlow<Boolean> =
        playback.status
            .map { it.episodeId == episodeId }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val downloadState: StateFlow<DownloadState?> =
        downloader.states
            .map { it[episodeId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val mutableConfirmation = MutableStateFlow<DownloadConfirmation?>(null)
    val downloadConfirmation: StateFlow<DownloadConfirmation?> = mutableConfirmation.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    // ---- 再生 ----

    /**
     * このエピソードから再生を始める。現在のエピソードなら開始ではなくトグル。
     *
     * 再生順は一覧と同じ規則で組む。番組に保存された絞り込み条件から一覧と同じ並びを 取り直すため、一覧画面を経由していなくても同じキューになる。
     */
    fun play() {
        if (isCurrent.value) {
            playback.togglePlayPause()
            return
        }
        val current = episode.value
        val currentFeed = feed.value
        if (current != null && currentFeed != null) {
            viewModelScope.launch { startPlayback(current, currentFeed) }
        }
    }

    private suspend fun startPlayback(current: Episode, currentFeed: Feed) {
        val episodes = feedRepository.observeEpisodes(current.feedId, currentFeed.filter).first()
        val order = PlaybackQueue.playbackOrderFrom(episodes, current.id)
        if (order.isEmpty()) {
            // 未DL、または絞り込みで一覧から外れているエピソード。どちらも鳴らす順番を組めない。
            mutableMessage.value = "ダウンロードしてから再生できます"
        } else {
            playback.play(order, currentFeed.playbackSpeed)
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekBack() = playback.seekBack()

    fun seekForward() = playback.seekForward()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    /** 速度は番組ごとに保存する。話速は番組によって大きく違うため。 */
    fun setSpeed(speed: Float) {
        playback.setSpeed(speed)
        val feedId = episode.value?.feedId ?: return
        viewModelScope.launch { feedRepository.updatePlaybackSpeed(feedId, speed) }
    }

    // ---- ダウンロード ----

    /** Wi-Fi なら即DL、モバイル回線なら確認を1回挟む(一覧と同じ規則)。 */
    fun requestDownload() {
        val current = episode.value ?: return
        downloader.clearFailure(current.id)
        if (networkState.isMetered()) {
            mutableConfirmation.value =
                DownloadConfirmation(current.id, current.title, current.enclosureSizeBytes)
        } else {
            startDownload(current.id)
        }
    }

    fun confirmDownload() {
        val confirmation = mutableConfirmation.value ?: return
        mutableConfirmation.value = null
        startDownload(confirmation.episodeId)
    }

    fun cancelDownload() {
        mutableConfirmation.value = null
    }

    private fun startDownload(episodeId: Long) {
        viewModelScope.launch { downloader.download(episodeId) }
    }

    // ---- 視聴状態 ----

    /**
     * 視聴済みを切り替える。
     *
     * 鳴っているエピソードを視聴済みにするのは「これはもう聴かなくていい」という判断なので、 記録だけ変えて鳴らし続けるのでは意図と結果が食い違う。再生を止め、キューを空にする。
     * 次を聴くかどうかは利用者が一覧で決める。
     */
    fun togglePlayed(onStopped: () -> Unit) {
        val current = episode.value ?: return
        val stopsPlayback = isCurrent.value && !current.played
        viewModelScope.launch {
            val nowPlayed = !current.played
            episodeRepository.setPlayed(current.id, nowPlayed)
            if (stopsPlayback) playback.stop()
            if (nowPlayed) {
                playedUndo.record(
                    UndoablePlayedChange(
                        message = playedMessage(current.downloaded, stopsPlayback),
                        snapshots = listOf(PlayedSnapshot(current.id, current.played)),
                        affectedEpisodeIds = listOf(current.id),
                    )
                )
            } else {
                mutableMessage.value = "未聴に戻しました"
            }
            // 鳴っているものを視聴済みにした画面は、もう再生コントロールの置き場所ではない。
            if (stopsPlayback) onStopped()
        }
    }

    fun consumeMessage() {
        mutableMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
