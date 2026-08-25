package dev.thinkami.podcastplayer.ui

/**
 * 再生時間の表示書式。1時間未満は `m:ss`、それ以上は `h:mm:ss`。
 *
 * 統合エピソード画面とミニプレイヤーで同じ書式を使う。桁や区切りが画面ごとに 揺れると、同じ時間が別の値に見えてしまう。
 */
fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
