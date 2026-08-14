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
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.logic.model.Feed
import dev.thinkami.podcastplayer.logic.model.PlayedSnapshot
import dev.thinkami.podcastplayer.player.PlaybackConnection
import dev.thinkami.podcastplayer.player.PlaybackStatus
import dev.thinkami.podcastplayer.ui.ArtworkSizes
import dev.thinkami.podcastplayer.ui.UndoablePlayedChange
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** モバイル回線でDLしようとしたときの確認待ち。 */
data class DownloadConfirmation(val episodeId: Long, val title: String, val sizeBytes: Long?)

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeListViewModel(
    private val feedId: Long,
    private val feedRepository: FeedRepository,
    private val episodeRepository: EpisodeRepository,
    private val downloader: EpisodeDownloader,
    private val networkState: NetworkStateProvider,
    private val playback: PlaybackConnection,
    artworkStore: ArtworkStore,
) : ViewModel() {

    val feed: StateFlow<Feed?> =
        feedRepository
            .observeFeed(feedId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * 上部バーに出す番組アートワーク1枚。取得できていなければ null(モノグラムを描く)。
     *
     * 同じパスに対する再デコードを避けるため、パスが変わったときだけ読み直す。
     */
    val artwork: StateFlow<Bitmap?> =
        feed
            .map { it?.artworkLocalPath }
            .distinctUntilChanged()
            .map { path -> artworkStore.load(path, ArtworkSizes.HEADER_TARGET_PX) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** 番組に保存された絞り込み条件を適用した一覧。条件を変えると自動で流れ直す。 */
    val episodes: StateFlow<List<Episode>> =
        feed
            .filterNotNull()
            .map { it.filter }
            .flatMapLatest { filter -> observeFiltered(filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val downloadStates: StateFlow<Map<Long, DownloadState>> = downloader.states

    /** いま鳴っているものの状態。現在のエピソードの行の表示と、play() のトグル読み替えに使う。 */
    val playbackStatus: StateFlow<PlaybackStatus> = playback.status

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    private val mutablePendingUndo = MutableStateFlow<UndoablePlayedChange?>(null)
    val pendingUndo: StateFlow<UndoablePlayedChange?> = mutablePendingUndo.asStateFlow()

    private val mutableConfirmation = MutableStateFlow<DownloadConfirmation?>(null)
    val downloadConfirmation: StateFlow<DownloadConfirmation?> = mutableConfirmation.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    private fun observeFiltered(filter: EpisodeFilter): Flow<List<Episode>> =
        feedRepository.observeEpisodes(feedId, filter)

    fun refresh() {
        viewModelScope.launch {
            mutableIsRefreshing.value = true
            try {
                feedRepository.refresh(feedId)
            } catch (e: IOException) {
                mutableMessage.value = e.message ?: "更新できませんでした"
            } finally {
                mutableIsRefreshing.value = false
            }
        }
    }

    fun toggleUnplayedOnly() {
        val current = feed.value?.filter ?: return
        updateFilter(current.copy(unplayedOnly = !current.unplayedOnly))
    }

    fun toggleDownloadedOnly() {
        val current = feed.value?.filter ?: return
        updateFilter(current.copy(downloadedOnly = !current.downloadedOnly))
    }

    private fun updateFilter(filter: EpisodeFilter) {
        viewModelScope.launch { feedRepository.updateFilter(feedId, filter) }
    }

    // ---- 視聴状態 ----

    /** 1件の視聴済みを切り替える。視聴済みにした場合だけ取り消しの猶予を置く。 未聴に戻す操作は非可逆な副作用がないため猶予は不要。 */
    fun togglePlayed(episode: Episode) {
        viewModelScope.launch {
            val nowPlayed = !episode.played
            episodeRepository.setPlayed(episode.id, nowPlayed)
            if (nowPlayed) {
                mutablePendingUndo.value =
                    UndoablePlayedChange(
                        // 何が起きるのかを明示する。黙ってファイルを消さない。
                        message =
                            if (episode.downloaded) {
                                "視聴済みにしました。ダウンロードを削除します"
                            } else {
                                "視聴済みにしました"
                            },
                        snapshots = listOf(PlayedSnapshot(episode.id, episode.played)),
                        affectedEpisodeIds = listOf(episode.id),
                    )
            } else {
                // 未聴に戻す操作にも結果を返す。視聴済みのときだけ無言、では一貫しない。
                mutableMessage.value = "未聴に戻しました"
            }
        }
    }

    fun markAllPlayed(played: Boolean) {
        viewModelScope.launch {
            val downloadedCount = episodeRepository.findEpisodes(feedId).count { it.downloaded }
            val snapshots = episodeRepository.setPlayedForFeed(feedId, played)
            if (played) {
                mutablePendingUndo.value =
                    UndoablePlayedChange(
                        message =
                            if (downloadedCount > 0) {
                                "${snapshots.size}件を視聴済みに。DL済み${downloadedCount}件を削除します"
                            } else {
                                "${snapshots.size}件を視聴済みにしました"
                            },
                        snapshots = snapshots,
                        affectedEpisodeIds = snapshots.map { it.episodeId },
                    )
            } else {
                mutableMessage.value = "${snapshots.size}件を未聴に戻しました"
            }
        }
    }

    /** 猶予が過ぎた。ここで初めてファイルを消す。 */
    fun commitPendingUndo() {
        val pending = mutablePendingUndo.value ?: return
        mutablePendingUndo.value = null
        viewModelScope.launch {
            episodeRepository.deleteDownloadsIfEligible(pending.affectedEpisodeIds)
        }
    }

    fun undoPending() {
        val pending = mutablePendingUndo.value ?: return
        mutablePendingUndo.value = null
        viewModelScope.launch { episodeRepository.restorePlayed(pending.snapshots) }
    }

    // ---- ダウンロード ----

    /** Wi-Fi なら即DL、モバイル回線なら確認を1回挟む。 */
    fun requestDownload(episode: Episode) {
        downloader.clearFailure(episode.id)
        if (networkState.isMetered()) {
            mutableConfirmation.value =
                DownloadConfirmation(episode.id, episode.title, episode.enclosureSizeBytes)
        } else {
            startDownload(episode.id)
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

    // ---- 再生 ----

    /** いま表示されている並び順のまま、DL済みだけを再生順にして渡す。 現在のエピソードだけは開始ではなくトグルに読み替える。 */
    fun play(episode: Episode) {
        // キューを組み直すと保存位置へシークし直す「リセット」になってしまうため、
        // 現在のエピソードは再生/一時停止の切り替えだけを行う。
        if (episode.id == playback.status.value.episodeId) {
            playback.togglePlayPause()
            return
        }
        val order = PlaybackQueue.playbackOrderFrom(episodes.value, episode.id)
        if (order.isEmpty()) {
            mutableMessage.value = "ダウンロードしてから再生できます"
            return
        }
        playback.play(order, feed.value?.playbackSpeed ?: Feed.DEFAULT_PLAYBACK_SPEED)
    }

    fun unsubscribe(onDone: () -> Unit) {
        viewModelScope.launch {
            feedRepository.unsubscribe(feedId)
            onDone()
        }
    }

    fun consumeMessage() {
        mutableMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
