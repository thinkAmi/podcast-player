package dev.thinkami.podcastplayer.ui.episodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.EpisodeRepository
import dev.thinkami.podcastplayer.logic.model.Episode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EpisodeDetailViewModel(episodeId: Long, episodeRepository: EpisodeRepository) : ViewModel() {

    val episode: StateFlow<Episode?> =
        episodeRepository
            .observeEpisode(episodeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
