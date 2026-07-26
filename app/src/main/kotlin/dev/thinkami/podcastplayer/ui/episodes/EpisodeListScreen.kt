package dev.thinkami.podcastplayer.ui.episodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.player.PlaybackStatus
import dev.thinkami.podcastplayer.ui.UndoablePlayedChange
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    viewModel: EpisodeListViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playbackStatus.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val confirmation by viewModel.downloadConfirmation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }

    UndoEffect(pendingUndo, snackbarHostState, viewModel::undoPending, viewModel::commitPendingUndo)
    MessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    Scaffold(
        modifier = modifier,
        topBar = {
            EpisodeListTopBar(
                title = feed?.title.orEmpty(),
                onBack = onBack,
                onMarkAllPlayed = { viewModel.markAllPlayed(true) },
                onMarkAllUnplayed = { viewModel.markAllPlayed(false) },
                onUnsubscribe = { showUnsubscribeConfirm = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            EpisodeList(
                filter = feed?.filter ?: EpisodeFilter.NONE,
                episodes = episodes,
                downloadStates = downloadStates,
                playbackStatus = playbackStatus,
                viewModel = viewModel,
                onOpenDetail = onOpenDetail,
            )
        }
    }

    Dialogs(
        showUnsubscribeConfirm = showUnsubscribeConfirm,
        feedTitle = feed?.title.orEmpty(),
        downloadedCount = episodes.count { it.downloaded },
        onUnsubscribe = {
            showUnsubscribeConfirm = false
            viewModel.unsubscribe(onBack)
        },
        onDismissUnsubscribe = { showUnsubscribeConfirm = false },
        downloadConfirmation = confirmation,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeListTopBar(
    title: String,
    onBack: () -> Unit,
    onMarkAllPlayed: () -> Unit,
    onMarkAllUnplayed: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
        actions = {
            FeedMenu(
                onMarkAllPlayed = onMarkAllPlayed,
                onMarkAllUnplayed = onMarkAllUnplayed,
                onUnsubscribe = onUnsubscribe,
            )
        },
    )
}

@Composable
private fun Dialogs(
    showUnsubscribeConfirm: Boolean,
    feedTitle: String,
    downloadedCount: Int,
    onUnsubscribe: () -> Unit,
    onDismissUnsubscribe: () -> Unit,
    downloadConfirmation: DownloadConfirmation?,
    viewModel: EpisodeListViewModel,
) {
    if (showUnsubscribeConfirm) {
        UnsubscribeConfirmDialog(
            feedTitle = feedTitle,
            downloadedCount = downloadedCount,
            onConfirm = onUnsubscribe,
            onDismiss = onDismissUnsubscribe,
        )
    }

    downloadConfirmation?.let { pending ->
        MeteredDownloadDialog(
            title = pending.title,
            sizeBytes = pending.sizeBytes,
            onConfirm = viewModel::confirmDownload,
            onDismiss = viewModel::cancelDownload,
        )
    }
}

@Composable
private fun EpisodeList(
    filter: EpisodeFilter,
    episodes: List<Episode>,
    downloadStates: Map<Long, DownloadState>,
    playbackStatus: PlaybackStatus,
    viewModel: EpisodeListViewModel,
    onOpenDetail: (Long) -> Unit,
) {
    LazyColumn {
        item {
            FilterChips(
                filter = filter,
                onToggleUnplayed = viewModel::toggleUnplayedOnly,
                onToggleDownloaded = viewModel::toggleDownloadedOnly,
            )
        }
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(
                episode = episode,
                downloadState = downloadStates[episode.id],
                onPlay = { viewModel.play(episode) },
                onDownload = { viewModel.requestDownload(episode) },
                onTogglePlayed = { viewModel.togglePlayed(episode) },
                onOpenDetail = { onOpenDetail(episode.id) },
                isCurrent = episode.id == playbackStatus.episodeId,
                isPlaying = playbackStatus.isPlaying,
            )
        }
    }
}

@Composable
private fun FilterChips(
    filter: EpisodeFilter,
    onToggleUnplayed: () -> Unit,
    onToggleDownloaded: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.unplayedOnly,
            onClick = onToggleUnplayed,
            label = { Text("未聴のみ") },
        )
        FilterChip(
            selected = filter.downloadedOnly,
            onClick = onToggleDownloaded,
            label = { Text("DL済みのみ") },
        )
    }
}

@Composable
private fun FeedMenu(
    onMarkAllPlayed: () -> Unit,
    onMarkAllUnplayed: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "番組のメニュー")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("すべて視聴済みにする") },
                onClick = {
                    expanded = false
                    onMarkAllPlayed()
                },
            )
            DropdownMenuItem(
                text = { Text("すべて未聴にする") },
                onClick = {
                    expanded = false
                    onMarkAllUnplayed()
                },
            )
            DropdownMenuItem(
                text = { Text("購読を削除") },
                onClick = {
                    expanded = false
                    onUnsubscribe()
                },
            )
        }
    }
}

/**
 * 購読削除の確認。
 *
 * 購読とエピソードの記録、ダウンロード済みファイルがまとめて消え、取り消しもできない。 このアプリで唯一の「まとめて壊せる」操作なので、ここだけは確認を挟む。
 */
@Composable
private fun UnsubscribeConfirmDialog(
    feedTitle: String,
    downloadedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("購読を削除しますか?") },
        text = {
            val downloads =
                if (downloadedCount > 0) {
                    "ダウンロード済みの${downloadedCount}件も削除されます。"
                } else {
                    ""
                }
            Text("「$feedTitle」の購読と視聴状態をすべて削除します。$downloads この操作は取り消せません。")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
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

private fun formatSize(sizeBytes: Long?): String {
    if (sizeBytes == null || sizeBytes <= 0L) return ""
    val megabytes = sizeBytes / 1024.0 / 1024.0
    return "%.0fMB ".format(megabytes)
}

/**
 * 取り消し猶予つきのスナックバー。
 *
 * 猶予のあいだに「元に戻す」が押されなければ、そこで初めてDLファイルを削除する。
 */
@Composable
private fun UndoEffect(
    pending: UndoablePlayedChange?,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
) {
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val result =
            withTimeoutOrNull(UndoablePlayedChange.UNDO_WINDOW_MS) {
                snackbarHostState.showSnackbar(
                    message = pending.message,
                    actionLabel = "元に戻す",
                    duration = SnackbarDuration.Indefinite,
                )
            }
        if (result == SnackbarResult.ActionPerformed) onUndo() else onCommit()
    }
}

@Composable
private fun MessageEffect(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumed()
        }
    }
}
