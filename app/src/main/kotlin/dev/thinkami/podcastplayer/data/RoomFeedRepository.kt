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
import dev.thinkami.podcastplayer.logic.rss.ImportSourceValidation
import dev.thinkami.podcastplayer.logic.rss.ImportSourceVerdict
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

    /**
     * 取り込みは購読済みの番組のエピソードだけを対象にする。
     *
     * `refresh()` を流用しないこと。あちらは channel のタイトルとアートワークをフィードへ書き戻すため、 アーカイブXMLの channel
     * 情報(「〜過去回アーカイブ(非公式)」など)で本物の番組名を 上書きしてしまう。
     */
    override suspend fun importEpisodes(feedUrl: String, importUrl: String): ImportOutcome {
        val normalizedUrl = feedUrl.trim()
        val feed = feedDao.findByUrl(normalizedUrl) ?: throw NotSubscribedException(normalizedUrl)

        val parsed = fetchAndParse(importUrl)
        val verdict =
            ImportSourceValidation.validate(parsed.items.map { it.sourceUrl }, feed.feedUrl)
        rejectionMessage(verdict)?.let { throw ImportSourceRejectedException(it) }

        val items = RssInterpretation.normalizeAll(parsed.items)
        val added = storeEpisodes(feed.id, items)
        return ImportOutcome(total = items.size, added = added)
    }

    private fun rejectionMessage(verdict: ImportSourceVerdict): String? =
        when (verdict) {
            ImportSourceVerdict.Allowed -> null
            ImportSourceVerdict.Undeclared -> "出典を申告しているitemがありません(取り込み用のアーカイブではありません)"
            is ImportSourceVerdict.Mixed ->
                "itemごとに違う出典が申告されています: ${verdict.declaredUrls.joinToString()}"
            is ImportSourceVerdict.Mismatched ->
                "出典が取り込み先と一致しません(申告: ${verdict.declared} / 取り込み先: ${verdict.subscribed})"
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
     * 新規エピソードを取り込み、既知のものはメタデータだけ更新する。戻り値は新規に追加された件数。
     *
     * 状態カラム(視聴済み・DL・再生位置)をフィードの再取得で上書きしないことが要点。
     */
    private suspend fun storeEpisodes(feedId: Long, items: List<NormalizedItem>): Int {
        val rowIds = episodeDao.insertIgnoringKnown(items.map { it.toEntity(feedId) })
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
        // 既知だった行の rowId は -1 になる。挿入された分だけを新規として数える。
        return rowIds.count { it != IGNORED_ROW_ID }
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

    private companion object {
        /** Room の OnConflictStrategy.IGNORE が「挿入しなかった」ことを表す rowId。 */
        const val IGNORED_ROW_ID = -1L
    }
}
