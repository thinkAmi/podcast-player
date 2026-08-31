package dev.thinkami.podcastplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.player.PlaybackStatus
import dev.thinkami.podcastplayer.ui.formatTime

/**
 * 一覧画面の下部に常駐する帯。
 *
 * OSの通知(画面上端から引き下ろすもの)とは別物で、こちらはアプリ内でプレイヤー画面へ 行くための入口を兼ねる。これがないと、アプリの中からシークバーや速度変更に辿り着けない。
 *
 * 進捗線と残り時間は表示だけで、シークはできない。この小さな帯での位置操作は誤タップに なりやすく、シークしたければタップして統合エピソード画面で行う。時間長が未確定のうちは
 * 出さない(満タンに見える進捗線や不正な残り時間を出さないため)。
 */
@Composable
fun MiniPlayer(
    episode: Episode,
    status: PlaybackStatus,
    onTogglePlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // ナビゲーションバーに重ならないようにする(edge-to-edge のため画面下端まで描画される)。
        Column(modifier = Modifier.clickable(onClick = onOpenPlayer).navigationBarsPadding()) {
            val hasDuration = status.durationMs > 0L
            if (hasDuration) {
                LinearProgressIndicator(
                    progress = {
                        (status.positionMs.toFloat() / status.durationMs).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                if (hasDuration) {
                    Text(
                        text = "残り ${formatTime(status.durationMs - status.positionMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector =
                            if (status.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (status.isPlaying) "一時停止" else "再生",
                    )
                }
            }
        }
    }
}
