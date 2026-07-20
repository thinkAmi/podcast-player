package dev.thinkami.podcastplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 一括操作を取り消すための、操作前の視聴状態のスナップショット。 */
data class EpisodePlayedState(val id: Long, val played: Boolean)

@Dao
interface EpisodeDao {

    /**
     * 番組のエピソード一覧。絞り込みは2条件のAND。
     *
     * 条件がオフのときは常に真になるよう書くことで、1本のクエリで4通りの組み合わせを賄う。 並びは新しい順。この順序がそのまま再生の自動継続の順序になる。
     */
    @Query(
        "SELECT * FROM episodes WHERE feedId = :feedId " +
            "AND (:unplayedOnly = 0 OR played = 0) " +
            "AND (:downloadedOnly = 0 OR downloaded = 1) " +
            "ORDER BY publishedAtEpochMillis DESC, id DESC"
    )
    fun observeFiltered(
        feedId: Long,
        unplayedOnly: Boolean,
        downloadedOnly: Boolean,
    ): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun findById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun observeById(episodeId: Long): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId")
    suspend fun findAllForFeed(feedId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId AND downloaded = 1")
    suspend fun findDownloadedForFeed(feedId: Long): List<EpisodeEntity>

    /**
     * フィード更新時の取り込み。
     *
     * 既知の guid は無視する。視聴済み・DL状態・再生位置といった利用者の状態を、フィードの 再取得で上書きしてはならないため。メタデータの更新は
     * [updateMetadataByGuid] で別途行う。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringKnown(episodes: List<EpisodeEntity>)

    /** 状態カラムには触れず、フィード由来のメタデータだけを更新する。 */
    @Query(
        "UPDATE episodes SET title = :title, showNotes = :showNotes, " +
            "publishedAtEpochMillis = :publishedAtEpochMillis, durationMs = :durationMs, " +
            "enclosureUrl = :enclosureUrl, enclosureSizeBytes = :enclosureSizeBytes " +
            "WHERE feedId = :feedId AND guid = :guid"
    )
    suspend fun updateMetadataByGuid(
        feedId: Long,
        guid: String,
        title: String,
        showNotes: String?,
        publishedAtEpochMillis: Long,
        durationMs: Long?,
        enclosureUrl: String,
        enclosureSizeBytes: Long?,
    )

    @Query("UPDATE episodes SET played = :played WHERE id = :episodeId")
    suspend fun setPlayed(episodeId: Long, played: Boolean)

    @Query("UPDATE episodes SET played = :played WHERE feedId = :feedId")
    suspend fun setPlayedForFeed(feedId: Long, played: Boolean)

    /** 一括操作の取り消しに使う。操作前の状態へ個別に戻す。 */
    @Query("UPDATE episodes SET played = :played WHERE id IN (:episodeIds)")
    suspend fun setPlayedForIds(episodeIds: List<Long>, played: Boolean)

    @Query("SELECT id, played FROM episodes WHERE feedId = :feedId")
    suspend fun findPlayedStates(feedId: Long): List<EpisodePlayedState>

    @Query(
        "UPDATE episodes SET downloaded = :downloaded, localPath = :localPath WHERE id = :episodeId"
    )
    suspend fun setDownloadState(episodeId: Long, downloaded: Boolean, localPath: String?)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :episodeId")
    suspend fun setPosition(episodeId: Long, positionMs: Long)

    @Query("UPDATE episodes SET favorite = :favorite WHERE id = :episodeId")
    suspend fun setFavorite(episodeId: Long, favorite: Boolean)
}
