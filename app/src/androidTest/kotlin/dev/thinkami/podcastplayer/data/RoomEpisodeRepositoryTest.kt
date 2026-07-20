package dev.thinkami.podcastplayer.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.data.db.EpisodeEntity
import dev.thinkami.podcastplayer.data.db.FeedEntity
import dev.thinkami.podcastplayer.data.db.PodcastDatabase
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 視聴済みに伴う自動削除。DB上の記録と再DL可能性を残すことが要点。 */
@RunWith(AndroidJUnit4::class)
class RoomEpisodeRepositoryTest {

    private lateinit var db: PodcastDatabase
    private lateinit var storage: MediaFileStorage
    private lateinit var repository: RoomEpisodeRepository
    private var feedId: Long = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java).build()
        storage = MediaFileStorage(context)
        repository = RoomEpisodeRepository(db.episodeDao(), storage)
        feedId =
            db.feedDao()
                .insert(
                    FeedEntity(
                        feedUrl = "https://example.test/feed.xml",
                        title = "番組",
                        artworkUrl = null,
                        artworkLocalPath = null,
                    )
                )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** DL済みのエピソードを、実体のあるファイル付きで用意する。 */
    private suspend fun downloadedEpisode(
        guid: String,
        played: Boolean = false,
        favorite: Boolean = false,
    ): Pair<Long, File> {
        db.episodeDao()
            .insertIgnoringKnown(
                listOf(
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
                        favorite = favorite,
                    )
                )
            )
        val entity = db.episodeDao().findAllForFeed(feedId).first { it.guid == guid }
        val file = storage.episodeFile(entity.id).apply { writeBytes(ByteArray(16)) }
        db.episodeDao()
            .setDownloadState(entity.id, downloaded = true, localPath = file.absolutePath)
        return entity.id to file
    }

    @Test
    fun 再生完了で視聴済みになりファイルが即座に消える() = runTest {
        val (episodeId, file) = downloadedEpisode("auto")

        repository.markPlaybackCompleted(episodeId)

        val after = repository.findEpisode(episodeId)!!
        assertTrue(after.played)
        assertFalse(after.downloaded)
        assertFalse(file.exists())
    }

    @Test
    fun 削除してもDB記録と音声URLは残り再DLできる() = runTest {
        val (episodeId, _) = downloadedEpisode("keep-url")

        repository.markPlaybackCompleted(episodeId)

        val after = repository.findEpisode(episodeId)!!
        assertEquals("https://example.test/keep-url.mp3", after.enclosureUrl)
        assertEquals(null, after.localPath)
    }

    @Test
    fun favoriteのエピソードは視聴済みでも削除されない() = runTest {
        val (episodeId, file) = downloadedEpisode("fav", favorite = true)

        repository.markPlaybackCompleted(episodeId)

        val after = repository.findEpisode(episodeId)!!
        assertTrue(after.played)
        assertTrue(after.downloaded)
        assertTrue(file.exists())
    }

    @Test
    fun 未聴のままなら削除されない() = runTest {
        val (episodeId, file) = downloadedEpisode("unplayed")

        repository.deleteDownloadsIfEligible(listOf(episodeId))

        assertTrue(file.exists())
        assertTrue(repository.findEpisode(episodeId)!!.downloaded)
    }

    @Test
    fun 手動で視聴済みにしただけではファイルは消えない() = runTest {
        val (episodeId, file) = downloadedEpisode("manual")

        repository.setPlayed(episodeId, played = true)

        // 取り消しの猶予があるため、この時点ではまだ消さない。
        assertTrue(file.exists())
        assertTrue(repository.findEpisode(episodeId)!!.downloaded)
    }

    @Test
    fun 猶予経過後の削除実行でファイルが消える() = runTest {
        val (episodeId, file) = downloadedEpisode("manual-commit")
        repository.setPlayed(episodeId, played = true)

        repository.deleteDownloadsIfEligible(listOf(episodeId))

        assertFalse(file.exists())
        assertFalse(repository.findEpisode(episodeId)!!.downloaded)
    }

    @Test
    fun 一括操作を取り消すと元から視聴済みだったものは戻さない() = runTest {
        val (alreadyPlayed, _) = downloadedEpisode("already", played = true)
        val (unplayed, _) = downloadedEpisode("fresh", played = false)

        val snapshot = repository.setPlayedForFeed(feedId, played = true)
        repository.restorePlayed(snapshot)

        assertTrue(repository.findEpisode(alreadyPlayed)!!.played)
        assertFalse(repository.findEpisode(unplayed)!!.played)
    }

    @Test
    fun 再生位置を保存できる() = runTest {
        val (episodeId, _) = downloadedEpisode("position")

        repository.savePosition(episodeId, 12_345L)

        assertEquals(12_345L, repository.findEpisode(episodeId)!!.positionMs)
    }
}
