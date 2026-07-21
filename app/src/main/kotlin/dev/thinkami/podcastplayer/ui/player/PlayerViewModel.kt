package dev.thinkami.podcastplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.EpisodeRepository
import dev.thinkami.podcastplayer.data.FeedRepository
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.player.PlaybackConnection
import dev.thinkami.podcastplayer.player.PlaybackStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 選べる再生速度。刻みを増やしすぎない(選択肢が多いこと自体が負担になる)。 */
val PLAYBACK_SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val playback: PlaybackConnection,
    private val feedRepository: FeedRepository,
    episodeRepository: EpisodeRepository,
) : ViewModel() {

    val status: StateFlow<PlaybackStatus> = playback.status

    /** いま鳴っているエピソード。ミニプレイヤーとプレイヤー画面の両方が読む。 */
    val currentEpisode: StateFlow<Episode?> =
        playback.status
            .map { it.episodeId }
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else episodeRepository.observeEpisode(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        // シークバーを進めるために定期的に状態を読み直す。
        viewModelScope.launch {
            while (true) {
                delay(PROGRESS_TICK_MS)
                playback.refreshStatus()
            }
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekBack() = playback.seekBack()

    fun seekForward() = playback.seekForward()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    /** 速度は番組ごとに保存する。話速は番組によって大きく違うため。 */
    fun setSpeed(speed: Float) {
        playback.setSpeed(speed)
        val feedId = currentEpisode.value?.feedId ?: return
        viewModelScope.launch { feedRepository.updatePlaybackSpeed(feedId, speed) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROGRESS_TICK_MS = 500L
    }
}
