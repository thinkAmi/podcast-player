package dev.thinkami.podcastplayer.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.FeedRepository
import dev.thinkami.podcastplayer.logic.model.Feed
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionListViewModel(private val feedRepository: FeedRepository) : ViewModel() {

    val feeds: StateFlow<List<Feed>> =
        feedRepository
            .observeFeeds()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    /** 引っ張って更新。全番組を順に取得する。 */
    fun refreshAll() {
        viewModelScope.launch {
            mutableIsRefreshing.value = true
            val failures = feedRepository.refreshAll()
            mutableIsRefreshing.value = false
            if (failures.isNotEmpty()) {
                mutableMessage.value =
                    "${failures.size}件の番組を更新できませんでした: ${failures.joinToString { it.title }}"
            }
        }
    }

    fun subscribe(feedUrl: String) {
        viewModelScope.launch {
            mutableIsRefreshing.value = true
            try {
                feedRepository.subscribe(feedUrl)
            } catch (e: IOException) {
                mutableMessage.value = e.message ?: "フィードを取得できませんでした"
            } catch (e: IllegalStateException) {
                mutableMessage.value = e.message
            } catch (e: IllegalArgumentException) {
                mutableMessage.value = e.message
            } finally {
                mutableIsRefreshing.value = false
            }
        }
    }

    fun consumeMessage() {
        mutableMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
