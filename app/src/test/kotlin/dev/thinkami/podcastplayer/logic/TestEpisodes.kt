package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode

/** テスト用のエピソード生成。必要な軸だけを名前付きで上書きできるようにする。 */
internal fun episode(
    id: Long = 1L,
    played: Boolean = false,
    downloaded: Boolean = false,
    favorite: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long? = 60_000L,
): Episode =
    Episode(
        id = id,
        feedId = 1L,
        guid = "guid-$id",
        title = "エピソード $id",
        showNotes = null,
        publishedAtEpochMillis = 0L,
        durationMs = durationMs,
        enclosureUrl = "https://example.test/$id.mp3",
        enclosureSizeBytes = null,
        played = played,
        downloaded = downloaded,
        localPath = if (downloaded) "/tmp/$id.mp3" else null,
        positionMs = positionMs,
        favorite = favorite,
    )
