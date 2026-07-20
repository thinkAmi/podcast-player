package dev.thinkami.podcastplayer.logic.model

/** 購読中の番組1件。 */
data class Feed(
    val id: Long,
    val feedUrl: String,
    val title: String,
    val artworkUrl: String?,
    val artworkLocalPath: String?,
    /** 番組ごとに永続化される絞り込み条件。 */
    val filter: EpisodeFilter,
    /** 番組ごとに保存される再生速度。話速は番組によって大きく違うため番組単位で持つ。 */
    val playbackSpeed: Float,
) {
    companion object {
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
    }
}
