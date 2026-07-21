package dev.thinkami.podcastplayer.data

import dev.thinkami.podcastplayer.data.db.EpisodeDao
import dev.thinkami.podcastplayer.data.db.EpisodeEntity
import dev.thinkami.podcastplayer.data.db.toModel
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.ListeningRules
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.PlayedSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomEpisodeRepository(
    private val episodeDao: EpisodeDao,
    private val storage: MediaFileStorage,
) : EpisodeRepository {

    override fun observeEpisode(episodeId: Long): Flow<Episode?> =
        episodeDao.observeById(episodeId).map { it?.toModel() }

    override suspend fun findEpisode(episodeId: Long): Episode? =
        episodeDao.findById(episodeId)?.toModel()

    override suspend fun findEpisodes(feedId: Long): List<Episode> =
        episodeDao.findAllForFeed(feedId).map(EpisodeEntity::toModel)

    override suspend fun setPlayed(episodeId: Long, played: Boolean) {
        episodeDao.setPlayed(episodeId, played)
    }

    override suspend fun setPlayedForFeed(feedId: Long, played: Boolean): List<PlayedSnapshot> {
        val before = episodeDao.findPlayedStates(feedId).map { PlayedSnapshot(it.id, it.played) }
        episodeDao.setPlayedForFeed(feedId, played)
        return before
    }

    override suspend fun restorePlayed(snapshots: List<PlayedSnapshot>) {
        // 元の状態ごとにまとめて戻す。個別UPDATEを件数ぶん撃たないため。
        snapshots
            .groupBy { it.played }
            .forEach { (played, group) ->
                episodeDao.setPlayedForIds(group.map { it.episodeId }, played)
            }
    }

    override suspend fun savePosition(episodeId: Long, positionMs: Long) {
        episodeDao.setPosition(episodeId, positionMs)
    }

    override suspend fun markPlaybackCompleted(episodeId: Long) {
        episodeDao.setPlayed(episodeId, true)
        deleteDownloadsIfEligible(listOf(episodeId))
    }

    override suspend fun deleteDownloadsIfEligible(episodeIds: List<Long>) {
        episodeIds.forEach { episodeId ->
            val episode = episodeDao.findById(episodeId)?.toModel() ?: return@forEach
            if (!ListeningRules.shouldDeleteDownload(episode)) return@forEach

            withContext(Dispatchers.IO) { storage.delete(episode.localPath) }
            // 記録と enclosureUrl は残す。再DLの可能性を閉ざさないため。
            episodeDao.setDownloadState(episodeId, downloaded = false, localPath = null)
        }
    }
}
