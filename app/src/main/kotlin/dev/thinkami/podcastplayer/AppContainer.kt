package dev.thinkami.podcastplayer

import android.content.Context
import dev.thinkami.podcastplayer.data.EpisodeRepository
import dev.thinkami.podcastplayer.data.FeedRepository
import dev.thinkami.podcastplayer.data.RoomEpisodeRepository
import dev.thinkami.podcastplayer.data.RoomFeedRepository
import dev.thinkami.podcastplayer.data.artwork.ArtworkStore
import dev.thinkami.podcastplayer.data.db.PodcastDatabase
import dev.thinkami.podcastplayer.data.download.EpisodeDownloader
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.net.NetworkStateProvider
import dev.thinkami.podcastplayer.data.rss.RssXmlReader
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.player.PlaybackConnection
import dev.thinkami.podcastplayer.ui.PlayedUndoHolder

/**
 * 手動DIコンテナ。アプリの生存期間中ひとつだけ存在する。
 *
 * 依存が増えたらここに `by lazy` を足していく。フレームワークを導入する前に、まず 「本当に必要な依存か」を疑うこと。
 */
class AppContainer(private val applicationContext: Context) {

    private val database: PodcastDatabase by lazy { PodcastDatabase.create(applicationContext) }

    private val httpFetcher: HttpFetcher by lazy { HttpFetcher() }

    val fileStorage: MediaFileStorage by lazy { MediaFileStorage(applicationContext) }

    val artworkStore: ArtworkStore by lazy { ArtworkStore(httpFetcher, fileStorage) }

    val networkState: NetworkStateProvider by lazy { NetworkStateProvider(applicationContext) }

    val playback: PlaybackConnection by lazy { PlaybackConnection(applicationContext) }

    val episodeRepository: EpisodeRepository by lazy {
        RoomEpisodeRepository(database.episodeDao(), fileStorage)
    }

    /** 視聴済み操作の取り消し猶予。画面をまたいで1件だけ持つ。 */
    val playedUndo: PlayedUndoHolder by lazy { PlayedUndoHolder(episodeRepository) }

    val downloader: EpisodeDownloader by lazy {
        EpisodeDownloader(httpFetcher, fileStorage, database.episodeDao())
    }

    val feedRepository: FeedRepository by lazy {
        RoomFeedRepository(
            feedDao = database.feedDao(),
            episodeDao = database.episodeDao(),
            fetcher = httpFetcher,
            reader = RssXmlReader(),
            artworkStore = artworkStore,
            storage = fileStorage,
        )
    }
}
