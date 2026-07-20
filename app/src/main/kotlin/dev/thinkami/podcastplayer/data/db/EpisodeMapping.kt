package dev.thinkami.podcastplayer.data.db

import dev.thinkami.podcastplayer.logic.rss.NormalizedItem

/**
 * フィード由来の項目を新規エピソードとして表現する。
 *
 * 状態カラムは既定値(未聴・未DL・位置0・favoriteなし)。既知の guid については この行は挿入されず、メタデータのみ別途更新される。
 */
fun NormalizedItem.toEntity(feedId: Long): EpisodeEntity =
    EpisodeEntity(
        feedId = feedId,
        guid = guid,
        title = title,
        showNotes = showNotes,
        publishedAtEpochMillis = publishedAtEpochMillis,
        durationMs = durationMs,
        enclosureUrl = enclosureUrl,
        enclosureSizeBytes = enclosureSizeBytes,
    )
