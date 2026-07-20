package dev.thinkami.podcastplayer.ui.player

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.player.PlaybackStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val episode by viewModel.currentEpisode.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("再生中") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ExpandMore, contentDescription = "閉じる")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = episode?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            ProgressSection(status = status, onSeek = viewModel::seekTo)
            TransportControls(
                isPlaying = status.isPlaying,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBack = viewModel::seekBack,
                onSeekForward = viewModel::seekForward,
            )
            SpeedSelector(current = status.speed, onSelect = viewModel::setSpeed)
            episode?.let { ShowNotesSection(it) }
        }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ShowNotesSection(episode: Episode) {
    val notes = episode.showNotes
    if (!notes.isNullOrBlank()) {
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
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
