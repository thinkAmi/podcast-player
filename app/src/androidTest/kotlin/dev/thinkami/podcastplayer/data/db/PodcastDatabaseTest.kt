package dev.thinkami.podcastplayer.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastDatabaseTest {

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

    private suspend fun insertFeed(url: String = "https://example.test/feed.xml"): Long =
        feedDao.insert(
            FeedEntity(feedUrl = url, title = "番組", artworkUrl = null, artworkLocalPath = null)
        )

    private fun episodeEntity(
        feedId: Long,
        guid: String,
        publishedAt: Long,
        played: Boolean = false,
        downloaded: Boolean = false,
    ) =
        EpisodeEntity(
            feedId = feedId,
            guid = guid,
            title = "エピソード $guid",
            showNotes = null,
            publishedAtEpochMillis = publishedAt,
            durationMs = 60_000L,
            enclosureUrl = "https://example.test/$guid.mp3",
            enclosureSizeBytes = null,
            played = played,
            downloaded = downloaded,
            localPath = if (downloaded) "/tmp/$guid.mp3" else null,
        )

    @Test
    fun 絞り込みなしなら全件が新しい順で返る() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(
            listOf(
                episodeEntity(feedId, "old", publishedAt = 100L),
                episodeEntity(feedId, "new", publishedAt = 200L),
            )
        )

        val result = episodeDao.observeFiltered(feedId, false, false).first()

        assertEquals(listOf("new", "old"), result.map { it.guid })
    }

    @Test
    fun 未聴かつDL済みのAND条件で絞り込める() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(
            listOf(
                episodeEntity(feedId, "a", 400L, played = false, downloaded = false),
                episodeEntity(feedId, "b", 300L, played = false, downloaded = true),
                episodeEntity(feedId, "c", 200L, played = true, downloaded = true),
                episodeEntity(feedId, "d", 100L, played = true, downloaded = false),
            )
        )

        val unplayedOnly = episodeDao.observeFiltered(feedId, true, false).first()
        val downloadedOnly = episodeDao.observeFiltered(feedId, false, true).first()
        val both = episodeDao.observeFiltered(feedId, true, true).first()

        assertEquals(listOf("a", "b"), unplayedOnly.map { it.guid })
        assertEquals(listOf("b", "c"), downloadedOnly.map { it.guid })
        assertEquals(listOf("b"), both.map { it.guid })
    }

    @Test
    fun フィード更新で既存エピソードの状態が保持される() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(listOf(episodeEntity(feedId, "keep", 100L)))
        val inserted = episodeDao.findAllForFeed(feedId).single()
        episodeDao.setPlayed(inserted.id, true)
        episodeDao.setDownloadState(inserted.id, downloaded = true, localPath = "/tmp/keep.mp3")
        episodeDao.setPosition(inserted.id, 42_000L)

        // 同じ guid を含むフィードを再取得したとみなして再投入する。
        episodeDao.insertIgnoringKnown(
            listOf(
                episodeEntity(feedId, "keep", 100L),
                episodeEntity(feedId, "fresh", 200L),
            )
        )

        val kept = episodeDao.findById(inserted.id)
        assertNotNull(kept)
        assertTrue(kept!!.played)
        assertTrue(kept.downloaded)
        assertEquals(42_000L, kept.positionMs)
        assertEquals(2, episodeDao.findAllForFeed(feedId).size)
    }

    @Test
    fun メタデータ更新は状態カラムに触れない() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(listOf(episodeEntity(feedId, "g", 100L)))
        val inserted = episodeDao.findAllForFeed(feedId).single()
        episodeDao.setPlayed(inserted.id, true)

        episodeDao.updateMetadataByGuid(
            feedId = feedId,
            guid = "g",
            title = "改題されたタイトル",
            showNotes = "追記されたショーノート",
            publishedAtEpochMillis = 150L,
            durationMs = 90_000L,
            enclosureUrl = "https://example.test/g2.mp3",
            enclosureSizeBytes = 1_000L,
        )

        val updated = episodeDao.findById(inserted.id)!!
        assertEquals("改題されたタイトル", updated.title)
        assertEquals(90_000L, updated.durationMs)
        assertTrue(updated.played)
    }

    @Test
    fun 番組単位で一括して視聴済みにできる() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(
            listOf(episodeEntity(feedId, "a", 200L), episodeEntity(feedId, "b", 100L))
        )

        episodeDao.setPlayedForFeed(feedId, played = true)

        assertTrue(episodeDao.findAllForFeed(feedId).all { it.played })
    }

    @Test
    fun 一括操作の取り消しのために操作前の状態を取得して戻せる() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(
            listOf(
                episodeEntity(feedId, "a", 300L, played = true),
                episodeEntity(feedId, "b", 200L, played = false),
                episodeEntity(feedId, "c", 100L, played = false),
            )
        )
        val before = episodeDao.findPlayedStates(feedId)

        episodeDao.setPlayedForFeed(feedId, played = true)
        // 取り消し: 操作前に未聴だったものだけ未聴へ戻す。
        episodeDao.setPlayedForIds(
            before.filterNot { it.played }.map { it.id },
            played = false,
        )

        val restored = episodeDao.findAllForFeed(feedId).associate { it.guid to it.played }
        assertEquals(mapOf("a" to true, "b" to false, "c" to false), restored)
    }

    @Test
    fun 購読削除でエピソードも消える() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(listOf(episodeEntity(feedId, "a", 100L)))

        feedDao.deleteById(feedId)

        assertTrue(episodeDao.findAllForFeed(feedId).isEmpty())
        assertNull(feedDao.findById(feedId))
    }

    @Test
    fun 絞り込み設定と再生速度は番組ごとに永続化される() = runTest {
        val first = insertFeed("https://example.test/a.xml")
        val second = insertFeed("https://example.test/b.xml")

        feedDao.updateFilter(first, unplayedOnly = true, downloadedOnly = true)
        feedDao.updatePlaybackSpeed(first, 1.5f)

        val updated = feedDao.findById(first)!!
        val untouched = feedDao.findById(second)!!
        assertTrue(updated.filterUnplayedOnly)
        assertTrue(updated.filterDownloadedOnly)
        assertEquals(1.5f, updated.playbackSpeed, 0.001f)
        assertFalse(untouched.filterUnplayedOnly)
        assertEquals(1.0f, untouched.playbackSpeed, 0.001f)
    }

    @Test
    fun DL済みのみを取得できる() = runTest {
        val feedId = insertFeed()
        episodeDao.insertIgnoringKnown(
            listOf(
                episodeEntity(feedId, "a", 200L, downloaded = true),
                episodeEntity(feedId, "b", 100L, downloaded = false),
            )
        )

        assertEquals(listOf("a"), episodeDao.findDownloadedForFeed(feedId).map { it.guid })
    }
}
