package dev.thinkami.podcastplayer.data.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.data.db.EpisodeEntity
import dev.thinkami.podcastplayer.data.db.FeedEntity
import dev.thinkami.podcastplayer.data.db.PodcastDatabase
import dev.thinkami.podcastplayer.data.net.FakeHttpServer
import dev.thinkami.podcastplayer.data.net.FakeResponse
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.DownloadFailure
import java.io.File
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [EpisodeDownloader] の計装テスト。
 *
 * 要点は「DL済み=再生可能」の保証が、完了・失敗・中断のどの抜け方でも崩れないこと。 中断は、依頼した画面を閉じたときに実際に起きた(受信し終えたのに保存が確定せず、 100%
 * の進捗表示が残り続けた)。
 */
@RunWith(AndroidJUnit4::class)
class EpisodeDownloaderTest {

    private lateinit var db: PodcastDatabase
    private lateinit var storage: MediaFileStorage
    private lateinit var server: FakeHttpServer
    private lateinit var job: CompletableJob
    private lateinit var downloader: EpisodeDownloader
    private var episodeId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java).build()
        storage = MediaFileStorage(context)
        server = FakeHttpServer()
        job = SupervisorJob()
        downloader =
            EpisodeDownloader(
                fetcher = HttpFetcher(),
                storage = storage,
                episodeDao = db.episodeDao(),
                scope = CoroutineScope(job + Dispatchers.Default),
            )
        val feedId =
            db.feedDao()
                .insert(
                    FeedEntity(
                        feedUrl = "https://example.test/feed.xml",
                        title = "番組",
                        artworkUrl = null,
                        artworkLocalPath = null,
                    )
                )
        db.episodeDao()
            .insertIgnoringKnown(
                listOf(
                    EpisodeEntity(
                        feedId = feedId,
                        guid = "ep",
                        title = "エピソード",
                        showNotes = null,
                        publishedAtEpochMillis = 0L,
                        durationMs = 60_000L,
                        enclosureUrl = server.url("/ep.mp3"),
                        enclosureSizeBytes = AUDIO.size.toLong(),
                    )
                )
            )
        episodeId = db.episodeDao().findAllForFeed(feedId).single().id
    }

    @After
    fun tearDown() {
        job.cancel()
        server.close()
        db.close()
        target().delete()
        partial().delete()
    }

    private fun target(): File = storage.episodeFile(episodeId)

    private fun partial(): File = File("${target().absolutePath}.part")

    /** 開始したDLがどの形であれ抜けきるまで待つ。 */
    private suspend fun awaitDownloads() {
        withTimeout(TIMEOUT_MS) { job.children.toList().joinAll() }
    }

    @Test
    fun 完了すると本来の名前で保存されDL済みになり進行中の表示が消える() = runBlocking {
        server.respondWith(FakeResponse.binary(AUDIO))

        downloader.start(episodeId)
        awaitDownloads()

        val episode = db.episodeDao().findById(episodeId)!!
        assertTrue(episode.downloaded)
        assertEquals(target().absolutePath, episode.localPath)
        assertArrayEquals(AUDIO, target().readBytes())
        assertFalse(partial().exists())
        assertNull(downloader.states.value[episodeId])
    }

    @Test
    fun 受信中に打ち切られても一時ファイルと進行中の表示を残さない() = runBlocking {
        val stall = FakeResponse.Stall(afterBytes = AUDIO.size / 2)
        server.respondWith(FakeResponse.stalledBinary(AUDIO, stall))

        downloader.start(episodeId)
        withTimeout(TIMEOUT_MS) {
            downloader.states.first {
                (it[episodeId] as? DownloadState.InProgress)?.bytesRead?.let { read -> read > 0 } ==
                    true
            }
        }
        // 実際の事故と同じ順序にする。打ち切られた後も受信は最後まで進み、抜けるところで中断が届く。
        job.cancel()
        stall.release.countDown()
        withTimeout(TIMEOUT_MS) { job.join() }

        assertFalse(partial().exists())
        assertFalse(target().exists())
        assertFalse(db.episodeDao().findById(episodeId)!!.downloaded)
        assertNull(downloader.states.value[episodeId])
    }

    @Test
    fun 失敗は失敗のまま表示され一時ファイルは残らない() = runBlocking {
        server.respondWith(FakeResponse.status(HTTP_NOT_FOUND, "Not Found"))

        downloader.start(episodeId)
        awaitDownloads()

        val state = downloader.states.value[episodeId]
        assertEquals(
            DownloadFailure.HttpStatus(HTTP_NOT_FOUND),
            (state as DownloadState.Failed).failure,
        )
        assertFalse(partial().exists())
        assertFalse(db.episodeDao().findById(episodeId)!!.downloaded)
    }

    private companion object {
        val AUDIO = ByteArray(256 * 1024) { (it % 251).toByte() }
        const val HTTP_NOT_FOUND = 404
        const val TIMEOUT_MS = 10_000L
    }
}
