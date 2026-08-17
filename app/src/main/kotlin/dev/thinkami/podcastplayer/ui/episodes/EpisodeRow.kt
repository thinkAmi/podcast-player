package dev.thinkami.podcastplayer.ui.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.DownloadFailurePresentation
import dev.thinkami.podcastplayer.logic.EpisodeAction
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.ui.episodeActionFor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * エピソード1行。
 *
 * タップできる領域は3つ:
 * - 左のアイコン: 未DLならダウンロード、DL済みなら再生。ただし現在のエピソード (再生キューがいま指している行)では再生状態を表示し、タップは再生/一時停止のトグルになる
 * - 右のチェック: 視聴済みの切り替え
 * - それ以外(本文): ショーノートのある詳細画面へ
 *
 * ストリーミングをしないため、ダウンロードと再生のアイコンが同時に並ぶことはない。 行のアイコンは常に2つで済み、ごちゃつかない。
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onTogglePlayed: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = modifier.clickable(onClick = onOpenDetail).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingAction(
            episode,
            downloadState,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onDownload = onDownload,
        )

        Column(
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = episode.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                text = episodeSubtitle(episode, downloadState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PlayedToggle(played = episode.played, onToggle = onTogglePlayed)
    }
}

@Composable
private fun LeadingAction(
    episode: Episode,
    downloadState: DownloadState?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    when (episodeActionFor(episode, downloadState, isCurrent)) {
        EpisodeAction.DOWNLOADING ->
            // 進み具合は行の副題にも百分率で出す。小さな円だけでは残量が読み取れないため。
            CircularProgressIndicator(
                progress = { (downloadState as? DownloadState.InProgress)?.fraction ?: 0f },
                modifier = Modifier.padding(horizontal = 12.dp).size(32.dp),
                strokeWidth = 3.dp,
            )
        EpisodeAction.TOGGLE_PLAY_PAUSE ->
            // 現在のエピソード。タップはトグルへ読み替えられる(EpisodeListViewModel.play)。
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "一時停止" else "再生",
                )
            }
        EpisodeAction.PLAY ->
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "再生")
            }
        EpisodeAction.RETRY_DOWNLOAD ->
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = "ダウンロードに失敗。もう一度試す",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        EpisodeAction.DOWNLOAD ->
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "ダウンロード")
            }
    }
}

@Composable
private fun PlayedToggle(played: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = if (played) "未聴に戻す" else "視聴済みにする",
            tint =
                if (played) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
        )
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private fun episodeSubtitle(episode: Episode, downloadState: DownloadState?): String {
    val parts = mutableListOf<String>()
    if (episode.publishedAtEpochMillis > 0L) {
        parts +=
            Instant.ofEpochMilli(episode.publishedAtEpochMillis)
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)
    }
    episode.durationMs?.let { parts += formatDuration(it) }
    when {
        downloadState is DownloadState.InProgress -> parts += downloadState.describe()
        downloadState is DownloadState.Failed -> parts += downloadState.describe()
        episode.downloaded -> parts += "DL済み"
    }
    return parts.joinToString(" ・ ")
}

/**
 * 失敗の理由と、勧められる場合だけ再試行の案内を出す。
 *
 * 案内を出さない種別でも再タップ自体は受け付ける(操作は奪わない)。案内が消えるだけ。
 */
private fun DownloadState.Failed.describe(): String {
    val label = DownloadFailurePresentation.label(failure)
    return if (DownloadFailurePresentation.suggestsRetry(failure)) {
        "$label ・ タップでやり直す"
    } else {
        label
    }
}

/** 全体サイズが分かるときは百分率、分からないときは受信済みのMBを出す。 */
private fun DownloadState.InProgress.describe(): String {
    val percent = fraction?.let { "%.0f%%".format(it * 100) }
    return if (percent != null) {
        "ダウンロード中 $percent"
    } else {
        "ダウンロード中 %.0fMB".format(bytesRead / 1024.0 / 1024.0)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}時間${minutes}分" else "${minutes}分"
}
