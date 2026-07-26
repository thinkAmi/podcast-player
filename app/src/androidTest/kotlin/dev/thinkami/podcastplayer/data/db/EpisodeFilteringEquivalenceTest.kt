package dev.thinkami.podcastplayer.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.logic.EpisodeFiltering
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SQL の WHERE 句([EpisodeDao.observeFiltered])と純粋 Kotlin([EpisodeFiltering])は同じ
 * 絞り込み条件の2通りの実装で、「同じ意味でなければならない」(EpisodeFiltering の class doc)。 その宣言をコメントのままにせず機械検証する。フィルター2条件 ×
 * エピソード状態 (played/downloaded)= 16通りの全数列挙。状態空間が全列挙可能なうちはランダム生成にしない。
 */
@RunWith(AndroidJUnit4::class)
class EpisodeFilteringEquivalenceTest {

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

    private fun episodeEntity(
        feedId: Long,
        guid: String,
        publishedAt: Long,
        played: Boolean,
        downloaded: Boolean,
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
    fun SQLの絞り込みと論理層の絞り込みは全16通りで一致する() = runTest {
        val feedId =
            feedDao.insert(
                FeedEntity(
                    feedUrl = "https://example.test/feed.xml",
                    title = "番組",
                    artworkUrl = null,
                    artworkLocalPath = null,
                )
            )
        // played × downloaded の全4状態を、公開日時が同値の組(タイブレーク id DESC の検証)と
        // 異なる組の両方で投入する
        val entities = buildList {
            var index = 0
            for (played in listOf(false, true)) {
                for (downloaded in listOf(false, true)) {
                    add(episodeEntity(feedId, "same-$index", 0L, played, downloaded))
                    add(
                        episodeEntity(feedId, "diff-$index", (index + 1) * 100L, played, downloaded)
                    )
                    index++
                }
            }
        }
        episodeDao.insertIgnoringKnown(entities)
        // 論理層への入力は「画面に出る並び」の規約(新しい順、同時刻は id 降順)で整列した全件
        val displayed =
            episodeDao
                .findAllForFeed(feedId)
                .sortedWith(
                    compareByDescending<EpisodeEntity> { it.publishedAtEpochMillis }
                        .thenByDescending { it.id }
                )
                .map { it.toModel() }

        for (unplayedOnly in listOf(false, true)) {
            for (downloadedOnly in listOf(false, true)) {
                val fromSql =
                    episodeDao.observeFiltered(feedId, unplayedOnly, downloadedOnly).first().map {
                        it.id
                    }
                val filter =
                    EpisodeFilter(unplayedOnly = unplayedOnly, downloadedOnly = downloadedOnly)
                val fromLogic = EpisodeFiltering.apply(displayed, filter).map { it.id }
                assertEquals(
                    "unplayedOnly=$unplayedOnly downloadedOnly=$downloadedOnly",
                    fromSql,
                    fromLogic,
                )
            }
        }
    }
}
