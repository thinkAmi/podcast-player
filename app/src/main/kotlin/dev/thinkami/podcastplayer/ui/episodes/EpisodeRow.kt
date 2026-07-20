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
import dev.thinkami.podcastplayer.logic.model.Episode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * エピソード1行。
 *
 * タップできる領域は3つ:
 * - 左のアイコン: 未DLならダウンロード、DL済みなら再生
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
) {
    Row(
        modifier = modifier.clickable(onClick = onOpenDetail).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingAction(episode, downloadState, onPlay = onPlay, onDownload = onDownload)

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
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    when {
        downloadState is DownloadState.InProgress ->
            CircularProgressIndicator(
                progress = { downloadState.fraction ?: 0f },
                modifier = Modifier.size(24.dp).padding(start = 12.dp),
            )
        episode.downloaded ->
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "再生")
            }
        downloadState is DownloadState.Failed ->
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = "ダウンロードに失敗。もう一度試す",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        else ->
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

private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

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
        downloadState is DownloadState.Failed -> parts += "DL失敗"
        episode.downloaded -> parts += "DL済み"
    }
    return parts.joinToString(" ・ ")
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}時間${minutes}分" else "${minutes}分"
}
