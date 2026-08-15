package dev.thinkami.podcastplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.EpisodeRepository
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

/**
 * アプリ全体で1つだけ持つ「いま鳴っているもの」。
 *
 * ミニプレイヤーの表示と、統合エピソード画面が読む再生状態の更新(シークバーを進めるための 定期的な読み直し)を受け持つ。エピソード1件に対する操作は統合エピソード画面の ViewModel
 * が持つため、ここには置かない。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val playback: PlaybackConnection,
    episodeRepository: EpisodeRepository,
) : ViewModel() {

    val status: StateFlow<PlaybackStatus> = playback.status

    /** いま鳴っているエピソード。ミニプレイヤーの表示と、追随・出し分けの判断に使う。 */
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROGRESS_TICK_MS = 500L
    }
}
