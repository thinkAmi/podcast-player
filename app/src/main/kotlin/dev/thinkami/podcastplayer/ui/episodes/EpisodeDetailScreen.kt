package dev.thinkami.podcastplayer.ui.episodes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.EpisodeAction
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.player.PlaybackStatus
import dev.thinkami.podcastplayer.ui.ArtworkImage
import dev.thinkami.podcastplayer.ui.ArtworkSizes
import dev.thinkami.podcastplayer.ui.episodeActionFor
import dev.thinkami.podcastplayer.ui.failureActionLabel
import dev.thinkami.podcastplayer.ui.showNotesToPlainText

/** 選べる再生速度。刻みを増やしすぎない(選択肢が多いこと自体が負担になる)。 */
val PLAYBACK_SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f)

/**
 * エピソード1件の画面。読む・ダウンロードする・聴くをここで完結させる。
 *
 * 画面はエピソードの現況を映すだけで、モードを持たない。鳴っているものなら再生の全操作が出て、 そうでなければDLか再生開始のボタンが出る。鳴っているものに追随して次の回へ切り替わるかどうかは
 * nav の担当で、この関数は与えられた1件を描くことに徹する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episode by viewModel.episode.collectAsStateWithLifecycle()
    val confirmation by viewModel.downloadConfirmation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    Scaffold(
        modifier = modifier,
        topBar = {
            DetailTopBar(
                played = episode?.played,
                onBack = onBack,
                onTogglePlayed = { viewModel.togglePlayed(onStopped = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        DetailBody(viewModel = viewModel, modifier = Modifier.padding(padding))
    }

    confirmation?.let { pending ->
        MeteredDownloadDialog(
            title = pending.title,
            sizeBytes = pending.sizeBytes,
            onConfirm = viewModel::confirmDownload,
            onDismiss = viewModel::cancelDownload,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(played: Boolean?, onBack: () -> Unit, onTogglePlayed: () -> Unit) {
    TopAppBar(
        title = { Text("エピソード", maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
        actions = { played?.let { PlayedToggle(played = it, onToggle = onTogglePlayed) } },
    )
}

@Composable
private fun DetailBody(viewModel: EpisodeDetailViewModel, modifier: Modifier = Modifier) {
    val episode by viewModel.episode.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isCurrent by viewModel.isCurrent.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArtworkImage(
            bitmap = artwork,
            title = feed?.title.orEmpty(),
            size = ArtworkSizes.PLAYER,
            cornerRadius = 12.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        Text(text = episode?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)

        episode?.let {
            EpisodeControls(
                episode = it,
                downloadState = downloadState,
                isCurrent = isCurrent,
                status = status,
                viewModel = viewModel,
            )
            ShowNotes(it)
        }
    }
}

/**
 * エピソードの状態ごとの操作。
 *
 * どの操作を出すかの判断は一覧の行と共有する([episodeActionFor])。ここが持つのは 「大きく描く」という見た目だけの違い。
 */
@Composable
private fun EpisodeControls(
    episode: Episode,
    downloadState: DownloadState?,
    isCurrent: Boolean,
    status: PlaybackStatus,
    viewModel: EpisodeDetailViewModel,
) {
    when (episodeActionFor(episode, downloadState, isCurrent)) {
        EpisodeAction.TOGGLE_PLAY_PAUSE -> {
            ProgressSection(status = status, onSeek = viewModel::seekTo)
            TransportControls(
                isPlaying = status.isPlaying,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBack = viewModel::seekBack,
                onSeekForward = viewModel::seekForward,
            )
            SpeedSelector(current = status.speed, onSelect = viewModel::setSpeed)
        }
        EpisodeAction.PLAY -> PrimaryAction("再生", Icons.Filled.PlayArrow, viewModel::play)
        EpisodeAction.DOWNLOAD ->
            PrimaryAction("ダウンロード", Icons.Filled.Download, viewModel::requestDownload)
        EpisodeAction.RETRY_DOWNLOAD ->
            // 一覧を経由せずこの画面でDLした場合、失敗の理由を知る場所はここしかない。
            // 再試行を勧めるかどうかの規則も行と共有する。
            PrimaryAction(
                label = downloadState.failureActionLabel(),
                icon = Icons.Filled.ErrorOutline,
                onClick = viewModel::requestDownload,
                tint = MaterialTheme.colorScheme.error,
            )
        EpisodeAction.DOWNLOADING -> DownloadProgress(downloadState)
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DownloadProgress(downloadState: DownloadState?) {
    val inProgress = downloadState as? DownloadState.InProgress
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { inProgress?.fraction ?: 0f },
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
        )
    }
    Text(
        text = inProgress?.let { describeProgress(it) } ?: "ダウンロード中",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** 全体サイズが分かるときは百分率、分からないときは受信済みのMBを出す(一覧の副題と同じ規則)。 */
private fun describeProgress(state: DownloadState.InProgress): String {
    val percent = state.fraction?.let { "%.0f%%".format(it * 100) }
    return if (percent != null) {
        "ダウンロード中 $percent"
    } else {
        "ダウンロード中 %.0fMB".format(state.bytesRead / 1024.0 / 1024.0)
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

@Composable
private fun ProgressSection(status: PlaybackStatus, onSeek: (Long) -> Unit) {
    Column {
        Slider(
            value = status.positionMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..status.durationMs.coerceAtLeast(1L).toFloat(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(status.positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(status.durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSeekBack) {
            Icon(Icons.Filled.Replay10, contentDescription = "10秒戻す")
        }
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                modifier = Modifier.size(48.dp),
            )
        }
        IconButton(onClick = onSeekForward) {
            Icon(Icons.Filled.Forward10, contentDescription = "10秒送る")
        }
    }
}

@Composable
private fun SpeedSelector(current: Float, onSelect: (Float) -> Unit) {
    // 速度の選択肢は画面幅に収まらないため横スクロールさせる。
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PLAYBACK_SPEEDS.forEach { speed ->
            FilterChip(
                selected = kotlin.math.abs(current - speed) < 0.01f,
                onClick = { onSelect(speed) },
                label = { Text("${speed}x") },
            )
        }
    }
}

@Composable
private fun ShowNotes(episode: Episode) {
    val notes = showNotesToPlainText(episode.showNotes)
    if (notes == null) {
        Text(
            text = "ショーノートはありません",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    } else {
        // フィードのdescriptionはHTMLを含むことがあるが、MVPではそのまま素のテキストとして出す。
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun MeteredDownloadDialog(
    title: String,
    sizeBytes: Long?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("モバイル回線です") },
        text = { Text("「$title」を${formatSize(sizeBytes)}ダウンロードしますか?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("ダウンロード") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

@Composable
private fun MessageEffect(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumed()
        }
    }
}

private fun formatSize(sizeBytes: Long?): String {
    if (sizeBytes == null || sizeBytes <= 0L) return ""
    val megabytes = sizeBytes / 1024.0 / 1024.0
    return "%.0fMB ".format(megabytes)
}

private fun formatTime(millis: Long): String {
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
