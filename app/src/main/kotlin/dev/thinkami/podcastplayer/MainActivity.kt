package dev.thinkami.podcastplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PodcastPlayerApp() }
    }
}

@Composable
private fun PodcastPlayerApp() {
    MaterialTheme { Text("Podcast Player") }
}
