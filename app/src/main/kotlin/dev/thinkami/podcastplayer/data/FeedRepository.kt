package dev.thinkami.podcastplayer.data

import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.logic.model.Feed
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem
import kotlinx.coroutines.flow.Flow

/**
 * 購読とフィード更新。
 *
 * interface にしておくのは、ViewModel のテストで手書きの Fake を差し込めるようにするため (モックライブラリは使わない)。
 */
interface FeedRepository {

    fun observeFeeds(): Flow<List<Feed>>

    /** 購読一覧画面用。各番組に未聴数を添えて流す。並びは observeFeeds と同じタイトル順。 */
    fun observeSubscriptionList(): Flow<List<SubscriptionListItem>>

    fun observeFeed(feedId: Long): Flow<Feed?>

    /** 番組のエピソード一覧。番組に保存された絞り込み条件を適用した結果を流す。 */
    fun observeEpisodes(feedId: Long, filter: EpisodeFilter): Flow<List<Episode>>

    /**
     * RSS URL を購読登録する。取得・パースに成功した場合のみ保存する。
     *
     * 失敗理由を型で返さず例外にするのは、UI 側で「エラーを表示して保存しない」以上の 分岐をしないため。
     */
    suspend fun subscribe(feedUrl: String): Long

    /** 購読を削除する。エピソード記録(CASCADE)とDL済みファイルも消す。 */
    suspend fun unsubscribe(feedId: Long)

    /** 1番組を更新する。既知の guid の状態(視聴済み・DL・再生位置)は保持する。 */
    suspend fun refresh(feedId: Long)

    /**
     * 購読済みの番組に、別のXMLからエピソードだけを取り込む。
     *
     * 配信側がフィードに載せなくなった過去回を、あとから同じ番組に合流させるための操作。 取り込み元は記憶せず、以後の更新で再取得もしない(購読ではなく一度きりの注入)。
     *
     * 取り違えを防ぐため、XMLが `<source url>` で申告する出典と [feedUrl] の一致を要求する。 一致しなければ1件も取り込まない。
     */
    suspend fun importEpisodes(feedUrl: String, importUrl: String): ImportOutcome

    /** 全番組を更新する。1番組の失敗で他を止めず、失敗した番組を返す。 */
    suspend fun refreshAll(): List<FeedRefreshFailure>

    suspend fun updateFilter(feedId: Long, filter: EpisodeFilter)

    suspend fun updatePlaybackSpeed(feedId: Long, speed: Float)
}

/** 更新に失敗した番組。全番組更新のあと、まとめて利用者に知らせるために使う。 */
data class FeedRefreshFailure(val feedId: Long, val title: String, val cause: Exception)

/** 取り込みの結果。総数と、そのうち新規に追加された件数。 */
data class ImportOutcome(val total: Int, val added: Int)

/** 購読していない番組へ取り込もうとしたことを表す。 */
class NotSubscribedException(feedUrl: String) : IllegalStateException("購読していません: $feedUrl")

/** 出典の申告が取り込み先と噛み合わず、取り込みを拒否したことを表す。 */
class ImportSourceRejectedException(message: String) : IllegalArgumentException(message)

/** 購読済みのURLを再登録しようとしたことを表す。 */
class AlreadySubscribedException(feedUrl: String) : IllegalStateException("すでに購読しています: $feedUrl")

/** 取得できたがポッドキャストのRSSとして解釈できなかったことを表す。 */
class NotAPodcastFeedException(feedUrl: String) :
    IllegalArgumentException("ポッドキャストのRSSとして解釈できませんでした: $feedUrl")
