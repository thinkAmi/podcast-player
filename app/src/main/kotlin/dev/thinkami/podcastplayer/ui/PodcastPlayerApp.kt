package dev.thinkami.podcastplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.thinkami.podcastplayer.AppContainer
import dev.thinkami.podcastplayer.ui.episodes.EpisodeDetailScreen
import dev.thinkami.podcastplayer.ui.episodes.EpisodeDetailViewModel
import dev.thinkami.podcastplayer.ui.episodes.EpisodeListScreen
import dev.thinkami.podcastplayer.ui.episodes.EpisodeListViewModel
import dev.thinkami.podcastplayer.ui.player.MiniPlayer
import dev.thinkami.podcastplayer.ui.player.PlayerScreen
import dev.thinkami.podcastplayer.ui.player.PlayerViewModel
import dev.thinkami.podcastplayer.ui.subscriptions.SubscriptionListScreen
import dev.thinkami.podcastplayer.ui.subscriptions.SubscriptionListViewModel

private object Routes {
    const val SUBSCRIPTIONS = "subscriptions"
    const val EPISODES = "feeds/{feedId}"
    const val DETAIL = "episodes/{episodeId}"
    const val PLAYER = "player"

    fun episodes(feedId: Long) = "feeds/$feedId"

    fun detail(episodeId: Long) = "episodes/$episodeId"
}

/**
 * 画面は4枚だけ。設定画面は作らない。
 *
 * ミニプレイヤーは一覧系の画面の下に常駐し、プレイヤー画面への入口を兼ねる。
 */
@Composable
fun PodcastPlayerApp(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    DisposableEffect(Unit) {
        container.playback.connect()
        onDispose { container.playback.release() }
    }

    val playerViewModel: PlayerViewModel = viewModel {
        PlayerViewModel(
            playback = container.playback,
            feedRepository = container.feedRepository,
            episodeRepository = container.episodeRepository,
            artworkStore = container.artworkStore,
        )
    }
    val currentEpisode by playerViewModel.currentEpisode.collectAsStateWithLifecycle()
    val status by playerViewModel.status.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Column(modifier = modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            container = container,
            playerViewModel = playerViewModel,
            modifier = Modifier.weight(1f),
        )

        // プレイヤー画面を開いているときはミニプレイヤーを重ねない。
        val episode = currentEpisode
        if (episode != null && currentRoute != Routes.PLAYER) {
            MiniPlayer(
                episode = episode,
                isPlaying = status.isPlaying,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SUBSCRIPTIONS,
        modifier = modifier,
    ) {
        composable(Routes.SUBSCRIPTIONS) {
            val vm: SubscriptionListViewModel = viewModel {
                SubscriptionListViewModel(container.feedRepository, container.artworkStore)
            }
            SubscriptionListScreen(
                viewModel = vm,
                onOpenFeed = { feedId -> navController.navigate(Routes.episodes(feedId)) },
            )
        }

        composable(
            Routes.EPISODES,
            arguments = listOf(navArgument("feedId") { type = NavType.LongType }),
        ) { entry ->
            val feedId = entry.arguments?.getLong("feedId") ?: return@composable
            val vm: EpisodeListViewModel = viewModel {
                EpisodeListViewModel(
                    feedId = feedId,
                    feedRepository = container.feedRepository,
                    episodeRepository = container.episodeRepository,
                    downloader = container.downloader,
                    networkState = container.networkState,
                    playback = container.playback,
                    artworkStore = container.artworkStore,
                )
            }
            EpisodeListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
            )
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
        ) { entry ->
            val episodeId = entry.arguments?.getLong("episodeId") ?: return@composable
            val vm: EpisodeDetailViewModel = viewModel {
                EpisodeDetailViewModel(episodeId, container.episodeRepository)
            }
            EpisodeDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                viewModel = playerViewModel,
                onBack = {
                    // 閉じるボタンとキュー終端の自動クローズが競合しても pop は一度だけに
                    // する(退場アニメーション中の二度目の呼び出しで一覧まで閉じない)。
                    if (navController.currentDestination?.route == Routes.PLAYER) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
