package dev.thinkami.podcastplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import dev.thinkami.podcastplayer.ui.player.PlayerViewModel
import dev.thinkami.podcastplayer.ui.subscriptions.SubscriptionListScreen
import dev.thinkami.podcastplayer.ui.subscriptions.SubscriptionListViewModel
import kotlinx.coroutines.withTimeoutOrNull

private object Routes {
    const val SUBSCRIPTIONS = "subscriptions"
    const val EPISODES = "feeds/{feedId}"
    const val DETAIL = "episodes/{episodeId}"
    const val EPISODE_ID_ARG = "episodeId"

    fun episodes(feedId: Long) = "feeds/$feedId"

    fun detail(episodeId: Long) = "episodes/$episodeId"
}

/**
 * 画面は3枚だけ。設定画面は作らない。
 *
 * エピソード1件の画面(統合エピソード画面)が、読む・DLする・聴くのすべてを引き受ける。 ミニプレイヤーは、その画面が鳴っているものを映していないときだけ下に出る。
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
            episodeRepository = container.episodeRepository,
        )
    }
    val currentEpisode by playerViewModel.currentEpisode.collectAsStateWithLifecycle()
    val status by playerViewModel.status.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // いま画面に出ているエピソード。統合エピソード画面でなければ null。
    val displayedEpisodeId =
        backStackEntry
            ?.takeIf { it.destination.route == Routes.DETAIL }
            ?.arguments
            ?.getLong(Routes.EPISODE_ID_ARG)

    FollowCurrentEpisode(navController) { status.episodeId }

    val undoHostState = remember { SnackbarHostState() }
    val pendingUndo by container.playedUndo.pending.collectAsStateWithLifecycle()
    UndoEffect(pendingUndo, undoHostState, container.playedUndo::undo, container.playedUndo::commit)

    // 鳴っているものを映している画面は、それ自身がプレイヤー。二重に操作を並べない。
    val episode = currentEpisode
    val showsMiniPlayer = episode != null && displayedEpisodeId != episode.id

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AppNavHost(navController = navController, container = container)
            // 取り消しの猶予は画面をまたいで1つ。視聴済みにして前の画面へ戻る経路があるため、
            // 表示もどの画面の上でも同じ場所に出す。
            //
            // edge-to-edge のため画面は下端まで描かれる。ミニプレイヤーがあるときはその上に
            // 重ねればよいが、無いときは自分でナビゲーションバーを避けないと「元に戻す」が
            // システムバーの下に潜り、押せなくなる。
            SnackbarHost(
                undoHostState,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .then(if (showsMiniPlayer) Modifier else Modifier.navigationBarsPadding()),
            )
        }

        if (episode != null && showsMiniPlayer) {
            MiniPlayer(
                episode = episode,
                isPlaying = status.isPlaying,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onOpenPlayer = { navController.navigate(Routes.detail(episode.id)) },
            )
        }
    }
}

/**
 * 鳴っているものを映している画面を、次のエピソードへ追随させる。
 *
 * 追随するのは「表示中 = 現在のエピソード」のときだけ。別の回を読んでいる最中に本文が 差し替わってはならない。
 *
 * 遷移は置き換え(直前の統合エピソード画面を pop してから push)にする。戻る操作で 聴き終えた回を延々と遡ることにならず、route と表示も常に一致する。
 *
 * キューが尽きたとき(null への遷移)は何もしない。画面は留まり、そのエピソードの現況を映す。 鳴り終えたことを理由に画面を閉じる判定は持たない。
 */
@Composable
private fun FollowCurrentEpisode(
    navController: NavHostController,
    currentEpisodeId: () -> Long?,
) {
    LaunchedEffect(navController) {
        var previous: Long? = null
        snapshotFlow(currentEpisodeId).collect { next ->
            val wasPlaying = previous
            previous = next
            if (wasPlaying == null || next == null || wasPlaying == next) return@collect
            if (navController.displayedEpisodeId() != wasPlaying) return@collect
            navController.navigate(Routes.detail(next)) {
                popUpTo(Routes.DETAIL) { inclusive = true }
            }
        }
    }
}

/**
 * 取り消し猶予つきのスナックバー。
 *
 * 猶予のあいだに「元に戻す」が押されなければ、そこで初めてDLファイルを削除する。 取り消しが戻すのは記録だけで、停止した再生は再開しない(聴き直すなら再生ボタンを押す)。
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

/** いま最前面にある統合エピソード画面が映しているエピソード。別の画面なら null。 */
private fun NavHostController.displayedEpisodeId(): Long? =
    currentBackStackEntry
        ?.takeIf { it.destination.route == Routes.DETAIL }
        ?.arguments
        ?.getLong(Routes.EPISODE_ID_ARG)

@Composable
private fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
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
                    playedUndo = container.playedUndo,
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
            arguments = listOf(navArgument(Routes.EPISODE_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            val episodeId = entry.arguments?.getLong(Routes.EPISODE_ID_ARG) ?: return@composable
            val vm: EpisodeDetailViewModel = viewModel {
                EpisodeDetailViewModel(
                    episodeId = episodeId,
                    episodeRepository = container.episodeRepository,
                    feedRepository = container.feedRepository,
                    downloader = container.downloader,
                    networkState = container.networkState,
                    playback = container.playback,
                    playedUndo = container.playedUndo,
                    artworkStore = container.artworkStore,
                )
            }
            EpisodeDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
