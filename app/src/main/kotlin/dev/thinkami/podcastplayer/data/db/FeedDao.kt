package dev.thinkami.podcastplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    /** 購読一覧。Flow を返すことで「DBを書けば画面が追随する」構造にする。 */
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    fun observeById(feedId: Long): Flow<FeedEntity?>

    @Query("SELECT * FROM feeds WHERE id = :feedId") suspend fun findById(feedId: Long): FeedEntity?

    @Query("SELECT * FROM feeds WHERE feedUrl = :feedUrl")
    suspend fun findByUrl(feedUrl: String): FeedEntity?

    @Query("SELECT * FROM feeds") suspend fun findAll(): List<FeedEntity>

    @Insert suspend fun insert(feed: FeedEntity): Long

    /** エピソードは外部キーの CASCADE で一緒に消える。ファイル削除は呼び出し側の責務。 */
    @Query("DELETE FROM feeds WHERE id = :feedId") suspend fun deleteById(feedId: Long)

    @Query(
        "UPDATE feeds SET filterUnplayedOnly = :unplayedOnly, filterDownloadedOnly = :downloadedOnly " +
            "WHERE id = :feedId"
    )
    suspend fun updateFilter(feedId: Long, unplayedOnly: Boolean, downloadedOnly: Boolean)

    @Query("UPDATE feeds SET playbackSpeed = :speed WHERE id = :feedId")
    suspend fun updatePlaybackSpeed(feedId: Long, speed: Float)

    @Query("UPDATE feeds SET title = :title, artworkUrl = :artworkUrl WHERE id = :feedId")
    suspend fun updateMetadata(feedId: Long, title: String, artworkUrl: String?)

    @Query("UPDATE feeds SET artworkLocalPath = :path WHERE id = :feedId")
    suspend fun updateArtworkLocalPath(feedId: Long, path: String?)
}
