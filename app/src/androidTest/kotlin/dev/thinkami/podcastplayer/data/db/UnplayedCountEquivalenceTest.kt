package dev.thinkami.podcastplayer.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.logic.ListeningRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SQL の COUNT([FeedDao.observeAllWithUnplayedCount])と純粋 Kotlin([ListeningRules.countUnplayed])
 * は同じ「未聴数」の2通りの実装で、「同じ意味でなければならない」(countUnplayed の KDoc)。 その宣言をコメントのままにせず機械検証する。エピソード状態
 * (played/downloaded)= 4通りの 全数列挙。状態空間が全列挙可能なうちはランダム生成にしない。エピソード0件のフィードで COUNT が 0 になること(LEFT JOIN
 * の検証)も含める。
 */
@RunWith(AndroidJUnit4::class)
class UnplayedCountEquivalenceTest {

    private lateinit var db: PodcastDatabase
    private lateinit var feedDao: FeedDao
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PodcastDatabase::class.java,
                )
                .build()
        feedDao = db.feedDao()
        episodeDao = db.episodeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun feedEntity(feedUrl: String, title: String) =
        FeedEntity(feedUrl = feedUrl, title = title, artworkUrl = null, artworkLocalPath = null)

    private fun episodeEntity(
        feedId: Long,
        guid: String,
        played: Boolean,
        downloaded: Boolean,
    ) =
        EpisodeEntity(
            feedId = feedId,
            guid = guid,
            title = "エピソード $guid",
            showNotes = null,
            publishedAtEpochMillis = 0L,
            durationMs = 60_000L,
            enclosureUrl = "https://example.test/$guid.mp3",
            enclosureSizeBytes = null,
            played = played,
            downloaded = downloaded,
            localPath = if (downloaded) "/tmp/$guid.mp3" else null,
        )

    @Test
    fun SQLのCOUNTと論理層のcountUnplayedは全状態で一致する() = runTest {
        // played × downloaded の全4状態を投入した番組と、エピソード0件の番組(LEFT JOIN の検証)
        val feedWithEpisodes = feedDao.insert(feedEntity("https://example.test/a.xml", "全状態"))
        val feedWithoutEpisodes = feedDao.insert(feedEntity("https://example.test/b.xml", "空"))

        val entities = buildList {
            var index = 0
            for (played in listOf(false, true)) {
                for (downloaded in listOf(false, true)) {
                    add(episodeEntity(feedWithEpisodes, "ep-$index", played, downloaded))
                    index++
                }
            }
        }
        episodeDao.insertIgnoringKnown(entities)

        val fromSql =
            feedDao.observeAllWithUnplayedCount().first().associate {
                it.feed.id to it.unplayedCount
            }

        for (feedId in listOf(feedWithEpisodes, feedWithoutEpisodes)) {
            val episodes = episodeDao.findAllForFeed(feedId).map { it.toModel() }
            assertEquals(
                "feedId=$feedId",
                ListeningRules.countUnplayed(episodes),
                fromSql.getValue(feedId),
            )
        }
        // 期待値を素で固定もしておく(参照実装ごと壊れる事故への保険)
        assertEquals(2, fromSql.getValue(feedWithEpisodes))
        assertEquals(0, fromSql.getValue(feedWithoutEpisodes))
    }
}
