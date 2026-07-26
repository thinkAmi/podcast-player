package dev.thinkami.podcastplayer.data

import dev.thinkami.podcastplayer.data.artwork.ArtworkStore
import dev.thinkami.podcastplayer.data.db.EpisodeDao
import dev.thinkami.podcastplayer.data.db.EpisodeEntity
import dev.thinkami.podcastplayer.data.db.FeedDao
import dev.thinkami.podcastplayer.data.db.FeedEntity
import dev.thinkami.podcastplayer.data.db.FeedWithUnplayedCount
import dev.thinkami.podcastplayer.data.db.toEntity
import dev.thinkami.podcastplayer.data.db.toModel
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.rss.RssXmlReader
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.logic.model.Feed
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem
import dev.thinkami.podcastplayer.logic.rss.NormalizedItem
import dev.thinkami.podcastplayer.logic.rss.ParsedFeed
import dev.thinkami.podcastplayer.logic.rss.RssInterpretation
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.xmlpull.v1.XmlPullParserException

class RoomFeedRepository(
    private val feedDao: FeedDao,
    private val episodeDao: EpisodeDao,
    private val fetcher: HttpFetcher,
    private val reader: RssXmlReader,
    private val artworkStore: ArtworkStore,
    private val storage: MediaFileStorage,
) : FeedRepository {

    override fun observeFeeds(): Flow<List<Feed>> =
        feedDao.observeAll().map { entities -> entities.map(FeedEntity::toModel) }

    override fun observeSubscriptionList(): Flow<List<SubscriptionListItem>> =
        feedDao.observeAllWithUnplayedCount().map { rows ->
            rows.map(FeedWithUnplayedCount::toModel)
        }

    override fun observeFeed(feedId: Long): Flow<Feed?> =
        feedDao.observeById(feedId).map { it?.toModel() }

    override fun observeEpisodes(feedId: Long, filter: EpisodeFilter): Flow<List<Episode>> =
        episodeDao.observeFiltered(feedId, filter.unplayedOnly, filter.downloadedOnly).map {
            entities ->
            entities.map(EpisodeEntity::toModel)
        }

    override suspend fun subscribe(feedUrl: String): Long {
        val normalizedUrl = feedUrl.trim()
        if (feedDao.findByUrl(normalizedUrl) != null) {
            throw AlreadySubscribedException(normalizedUrl)
        }

        val parsed = fetchAndParse(normalizedUrl)
        val items = RssInterpretation.normalizeAll(parsed.items)
        // タイトルもエピソードも取れないものはポッドキャストのRSSではないとみなす。
        if (parsed.title.isNullOrBlank() && items.isEmpty()) {
            throw NotAPodcastFeedException(normalizedUrl)
        }

        val feedId =
            feedDao.insert(
                FeedEntity(
                    feedUrl = normalizedUrl,
                    title = parsed.title?.trim().orEmpty().ifEmpty { normalizedUrl },
                    artworkUrl = parsed.artworkUrl,
                    artworkLocalPath = null,
                )
            )
        storeEpisodes(feedId, items)
        cacheArtwork(feedId, parsed.artworkUrl)
        return feedId
    }

    override suspend fun unsubscribe(feedId: Long) {
        // DB上はCASCADEで消えるが、ファイルは自分で消す必要がある。
        episodeDao.findDownloadedForFeed(feedId).forEach { storage.delete(it.localPath) }
        feedDao.findById(feedId)?.let { storage.delete(it.artworkLocalPath) }
        feedDao.deleteById(feedId)
    }

    override suspend fun refresh(feedId: Long) {
        val feed = feedDao.findById(feedId) ?: return
        val parsed = fetchAndParse(feed.feedUrl)
        val items = RssInterpretation.normalizeAll(parsed.items)

        storeEpisodes(feedId, items)
        parsed.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { title ->
                feedDao.updateMetadata(feedId, title, parsed.artworkUrl)
            }
        if (feed.artworkLocalPath == null) {
            cacheArtwork(feedId, parsed.artworkUrl)
        }
    }

    override suspend fun refreshAll(): List<FeedRefreshFailure> =
        // 1番組の失敗で他を止めない。成功したぶんだけ取り込み、失敗は呼び出し側へ返す。
        feedDao.findAll().mapNotNull { feed ->
            try {
                refresh(feed.id)
                null
            } catch (e: IOException) {
                FeedRefreshFailure(feed.id, feed.title, e)
            } catch (e: XmlPullParserException) {
                FeedRefreshFailure(feed.id, feed.title, e)
            }
        }

    override suspend fun updateFilter(feedId: Long, filter: EpisodeFilter) {
        feedDao.updateFilter(feedId, filter.unplayedOnly, filter.downloadedOnly)
    }

    override suspend fun updatePlaybackSpeed(feedId: Long, speed: Float) {
        feedDao.updatePlaybackSpeed(feedId, speed)
    }

    private suspend fun fetchAndParse(feedUrl: String): ParsedFeed {
        val xml = fetcher.fetchText(feedUrl)
        return try {
            reader.read(xml)
        } catch (e: XmlPullParserException) {
            throw NotAPodcastFeedException(feedUrl).initCause(e) as NotAPodcastFeedException
        }
    }

    /**
     * 新規エピソードを取り込み、既知のものはメタデータだけ更新する。
     *
     * 状態カラム(視聴済み・DL・再生位置)をフィードの再取得で上書きしないことが要点。
     */
    private suspend fun storeEpisodes(feedId: Long, items: List<NormalizedItem>) {
        episodeDao.insertIgnoringKnown(items.map { it.toEntity(feedId) })
        items.forEach { item ->
            episodeDao.updateMetadataByGuid(
                feedId = feedId,
                guid = item.guid,
                title = item.title,
                showNotes = item.showNotes,
                publishedAtEpochMillis = item.publishedAtEpochMillis,
                durationMs = item.durationMs,
                enclosureUrl = item.enclosureUrl,
                enclosureSizeBytes = item.enclosureSizeBytes,
            )
        }
    }

    private suspend fun cacheArtwork(feedId: Long, artworkUrl: String?) {
        try {
            artworkStore.ensureCached(feedId, artworkUrl)?.let { path ->
                feedDao.updateArtworkLocalPath(feedId, path)
            }
        } catch (_: IOException) {
            // アートワークは無くても聴取に支障がないため、取得失敗は購読・更新を妨げない。
        }
    }
}
