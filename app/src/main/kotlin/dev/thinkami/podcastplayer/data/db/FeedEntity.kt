package dev.thinkami.podcastplayer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.logic.model.Feed

@Entity(tableName = "feeds", indices = [Index(value = ["feedUrl"], unique = true)])
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val feedUrl: String,
    val title: String,
    val artworkUrl: String?,
    val artworkLocalPath: String?,
    /** 絞り込み条件は番組ごとに永続化する。既定は両方オフ(全件表示)。 */
    val filterUnplayedOnly: Boolean = false,
    val filterDownloadedOnly: Boolean = false,
    val playbackSpeed: Float = Feed.DEFAULT_PLAYBACK_SPEED,
)

fun FeedEntity.toModel(): Feed =
    Feed(
        id = id,
        feedUrl = feedUrl,
        title = title,
        artworkUrl = artworkUrl,
        artworkLocalPath = artworkLocalPath,
        filter =
            EpisodeFilter(
                unplayedOnly = filterUnplayedOnly,
                downloadedOnly = filterDownloadedOnly,
            ),
        playbackSpeed = playbackSpeed,
    )
