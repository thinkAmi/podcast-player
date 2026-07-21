package dev.thinkami.podcastplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.thinkami.podcastplayer.ui.PodcastPlayerApp
import dev.thinkami.podcastplayer.ui.theme.PodcastPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PodcastPlayerTheme { PodcastPlayerApp(container = appContainer) } }
    }
}
