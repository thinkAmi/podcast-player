package dev.thinkami.podcastplayer.ui.subscriptions

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thinkami.podcastplayer.data.FeedRepository
import dev.thinkami.podcastplayer.data.artwork.ArtworkStore
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem
import dev.thinkami.podcastplayer.ui.ArtworkSizes
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionListViewModel(
    private val feedRepository: FeedRepository,
    private val artworkStore: ArtworkStore,
) : ViewModel() {

    val feeds: StateFlow<List<SubscriptionListItem>> =
        feedRepository
            .observeSubscriptionList()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** デコード済みのアートワーク。番組数ぶん(数枚〜十数枚)しかないので破棄せず持ち続ける。 */
    private val decoded = mutableMapOf<Long, Bitmap>()

    /**
     * 表示用に縮小デコードしたアートワーク。取得できない番組は入らず、行はモノグラムを描く。
     *
     * デコードは一覧の内容が変わったときだけで、再コンポジションやスクロールでは走らない。
     */
    val artworks: StateFlow<Map<Long, Bitmap>> =
        feeds
            .map { items -> decodeMissing(items) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private suspend fun decodeMissing(items: List<SubscriptionListItem>): Map<Long, Bitmap> {
        items
            .filterNot { decoded.containsKey(it.feed.id) }
            .forEach { item ->
                val bitmap =
                    artworkStore.load(item.feed.artworkLocalPath, ArtworkSizes.THUMBNAIL_TARGET_PX)
                if (bitmap != null) decoded[item.feed.id] = bitmap
            }
        return decoded.toMap()
    }

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
