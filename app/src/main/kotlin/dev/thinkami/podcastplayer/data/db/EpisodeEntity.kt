package dev.thinkami.podcastplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.thinkami.podcastplayer.logic.model.Episode

@Entity(
    tableName = "episodes",
    foreignKeys =
        [
            ForeignKey(
                entity = FeedEntity::class,
                parentColumns = ["id"],
                childColumns = ["feedId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    // guid は番組内で一意。フィード更新時の既知/新規判定に使う。
    indices = [Index(value = ["feedId", "guid"], unique = true), Index(value = ["feedId"])],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val feedId: Long,
    val guid: String,
    val title: String,
    val showNotes: String?,
    val publishedAtEpochMillis: Long,
    val durationMs: Long?,
    /** 削除後の再DLに備えて保持し続ける。 */
    val enclosureUrl: String,
    val enclosureSizeBytes: Long?,
    val played: Boolean = false,
    val downloaded: Boolean = false,
    val localPath: String? = null,
    val positionMs: Long = 0L,
    /** MVPではUIを持たないが、自動削除の除外判定に初日から使う。 */
    val favorite: Boolean = false,
)

fun EpisodeEntity.toModel(): Episode =
    Episode(
        id = id,
        feedId = feedId,
        guid = guid,
        title = title,
        showNotes = showNotes,
        publishedAtEpochMillis = publishedAtEpochMillis,
        durationMs = durationMs,
        enclosureUrl = enclosureUrl,
        enclosureSizeBytes = enclosureSizeBytes,
        played = played,
        downloaded = downloaded,
        localPath = localPath,
        positionMs = positionMs,
        favorite = favorite,
    )
