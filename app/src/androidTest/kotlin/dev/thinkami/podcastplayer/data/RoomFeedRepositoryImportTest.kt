package dev.thinkami.podcastplayer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.data.artwork.ArtworkStore
import dev.thinkami.podcastplayer.data.db.PodcastDatabase
import dev.thinkami.podcastplayer.data.net.FakeHttpServer
import dev.thinkami.podcastplayer.data.net.FakeResponse
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.rss.RssXmlReader
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 既存購読へのエピソード取り込み。
 *
 * 「取り込めること」より「取り違えたときに取り込まないこと」と「既にあるものを壊さないこと」が 検証の主眼。エピソード個別の削除手段がないため、誤って取り込むと復旧が重い。
 */
@RunWith(AndroidJUnit4::class)
class RoomFeedRepositoryImportTest {

    private lateinit var db: PodcastDatabase
    private lateinit var server: FakeHttpServer
    private lateinit var repository: RoomFeedRepository
    private lateinit var feedUrl: String

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java).build()
        server = FakeHttpServer()
        val fetcher = HttpFetcher()
        val storage = MediaFileStorage(context)
        repository =
            RoomFeedRepository(
                feedDao = db.feedDao(),
                episodeDao = db.episodeDao(),
                fetcher = fetcher,
                reader = RssXmlReader(),
                artworkStore = ArtworkStore(fetcher, storage),
                storage = storage,
            )

        feedUrl = server.url("/feed.xml")
        server.respondWith(FakeResponse.plainText(currentFeed()))
        repository.subscribe(feedUrl)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun 出典が一致すれば取り込まれ既存エピソードと日付順に並ぶ() = runTest {
        server.respondWith(FakeResponse.plainText(archive(sourceUrl = feedUrl)))

        val outcome = repository.importEpisodes(feedUrl, server.url("/archive.xml"))

        assertEquals(2, outcome.total)
        assertEquals(2, outcome.added)
        val titles = episodeTitles()
        // 新しい順。取り込んだ過去回(2017年)は既存(2026年)の後ろに続く。
        assertEquals(listOf("第2回", "第1回", "第0.5回", "第0回"), titles)
    }

    @Test
    fun 取り込んでも番組のメタデータは変わらない() = runTest {
        val before = repository.observeFeeds().first().single()
        server.respondWith(FakeResponse.plainText(archive(sourceUrl = feedUrl)))

        repository.importEpisodes(feedUrl, server.url("/archive.xml"))

        val after = repository.observeFeeds().first().single()
        assertEquals(before.title, after.title)
        assertEquals(before.artworkUrl, after.artworkUrl)
        assertEquals(before.feedUrl, after.feedUrl)
    }

    @Test
    fun 同じXMLを二度取り込んでも増えず既存の状態も変わらない() = runTest {
        server.respondWith(FakeResponse.plainText(archive(sourceUrl = feedUrl)))
        repository.importEpisodes(feedUrl, server.url("/archive.xml"))
        val target = db.episodeDao().findAllForFeed(feedIdOf()).first { it.guid == "old-1" }
        db.episodeDao().setPlayed(target.id, played = true)
        db.episodeDao().setPosition(target.id, positionMs = 12_345L)

        val outcome = repository.importEpisodes(feedUrl, server.url("/archive.xml"))

        assertEquals(2, outcome.total)
        assertEquals(0, outcome.added)
        assertEquals(4, db.episodeDao().findAllForFeed(feedIdOf()).size)
        val reloaded = db.episodeDao().findAllForFeed(feedIdOf()).first { it.guid == "old-1" }
        assertTrue(reloaded.played)
        assertEquals(12_345L, reloaded.positionMs)
    }

    @Test
    fun 出典を申告しないXMLは取り込まれない() = runTest {
        // 公式フィードそのものを取り込もうとした場合がこれにあたる
        assertRejected(archive(sourceUrl = null), ImportSourceRejectedException::class.java)
    }

    @Test
    fun 別番組の出典を申告するXMLは取り込まれない() = runTest {
        assertRejected(
            archive(sourceUrl = "https://other.test/rss"),
            ImportSourceRejectedException::class.java,
        )
    }

    @Test
    fun 出典が混在するXMLは取り込まれない() = runTest {
        assertRejected(
            archive(sourceUrl = feedUrl, secondSourceUrl = "https://other.test/rss"),
            ImportSourceRejectedException::class.java,
        )
    }

    @Test
    fun 購読していないフィードには取り込めない() = runTest {
        assertRejected(
            archive(sourceUrl = feedUrl),
            NotSubscribedException::class.java,
            targetFeedUrl = "https://unknown.test/feed.xml",
        )
    }

    /** 拒否されること、かつ既存のエピソードが1件も増減しないことを確かめる。 */
    private suspend fun assertRejected(
        archiveXml: String,
        expected: Class<out Exception>,
        targetFeedUrl: String = feedUrl,
    ) {
        server.respondWith(FakeResponse.plainText(archiveXml))
        val before = db.episodeDao().findAllForFeed(feedIdOf()).size

        val thrown = runCatching {
            repository.importEpisodes(targetFeedUrl, server.url("/archive.xml"))
        }
            .exceptionOrNull()

        assertTrue("拒否されなかった: $thrown", expected.isInstance(thrown))
        assertEquals(before, db.episodeDao().findAllForFeed(feedIdOf()).size)
    }

    @Test
    fun 出典付きXMLは通常の購読登録では検証されない() = runTest {
        // 出典の検証は取り込み経路だけの関心。別番組として購読することは妨げない。
        server.respondWith(FakeResponse.plainText(archive(sourceUrl = "https://other.test/rss")))

        repository.subscribe(server.url("/archive.xml"))

        assertEquals(2, repository.observeFeeds().first().size)
    }

    private suspend fun feedIdOf(): Long = repository.observeFeeds().first().first().id

    private suspend fun episodeTitles(): List<String> =
        repository
            .observeEpisodes(
                feedIdOf(),
                EpisodeFilter(unplayedOnly = false, downloadedOnly = false),
            )
            .first()
            .map { it.title }

    private fun currentFeed(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>テスト番組</title>
            <item>
              <title>第2回</title>
              <guid>now-2</guid>
              <pubDate>Sun, 19 Jul 2026 09:00:00 +0900</pubDate>
              <enclosure url="https://example.test/ep2.mp3" length="1000" />
            </item>
            <item>
              <title>第1回</title>
              <guid>now-1</guid>
              <pubDate>Sat, 18 Jul 2026 09:00:00 +0900</pubDate>
              <enclosure url="https://example.test/ep1.mp3" length="1000" />
            </item>
          </channel>
        </rss>
        """
            .trimIndent()

    /** 取り込み用アーカイブ。[sourceUrl] が null なら出典を申告しない。 */
    private fun archive(sourceUrl: String?, secondSourceUrl: String? = null): String {
        fun source(url: String?) = url?.let { "<source url=\"$it\">テスト番組</source>" }.orEmpty()
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>テスト番組 過去回アーカイブ(非公式)</title>
            <item>
              <title>第0.5回</title>
              <guid>old-2</guid>
              <pubDate>Tue, 07 Feb 2017 09:00:00 +0900</pubDate>
              <enclosure url="https://example.test/old2.mp3" length="1000" />
              ${source(sourceUrl)}
            </item>
            <item>
              <title>第0回</title>
              <guid>old-1</guid>
              <pubDate>Mon, 06 Feb 2017 09:00:00 +0900</pubDate>
              <enclosure url="https://example.test/old1.mp3" length="1000" />
              ${source(secondSourceUrl ?: sourceUrl)}
            </item>
          </channel>
        </rss>
        """
            .trimIndent()
    }
}
