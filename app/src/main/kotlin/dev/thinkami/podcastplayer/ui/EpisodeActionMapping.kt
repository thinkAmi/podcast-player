package dev.thinkami.podcastplayer.ui

import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.DownloadFailure
import dev.thinkami.podcastplayer.logic.DownloadFailurePresentation
import dev.thinkami.podcastplayer.logic.EpisodeAction
import dev.thinkami.podcastplayer.logic.EpisodeActions
import dev.thinkami.podcastplayer.logic.model.Episode

/**
 * 画面が持っている型([Episode] と [DownloadState])を、判断そのものを持つ [EpisodeActions] の入力へ 変換する。
 *
 * 判断は logic/ にあり、data 層の型を知らない。その橋渡しだけをここに置くことで、 一覧の行と統合エピソード画面が同じ判断を共有できる。
 */
fun episodeActionFor(
    episode: Episode,
    downloadState: DownloadState?,
    isCurrent: Boolean,
): EpisodeAction =
    EpisodeActions.actionFor(
        isDownloading = downloadState is DownloadState.InProgress,
        isCurrent = isCurrent,
        isDownloaded = episode.downloaded,
        hasFailedDownload = downloadState is DownloadState.Failed,
    )

/**
 * 失敗した操作に付ける名前。一覧の行のアイコンと統合エピソード画面のボタンで共有する。
 *
 * [EpisodeAction.RETRY_DOWNLOAD] は [DownloadState.Failed] のときにしか現れないが、
 * 型の上ではそれを保証できない。種別が読めない場合は接続不能として扱う (もっとも起きやすい失敗であり、案内を出す側に倒しても操作は妨げないため)。
 */
fun DownloadState?.failureActionLabel(): String =
    DownloadFailurePresentation.actionLabel(
        (this as? DownloadState.Failed)?.failure ?: DownloadFailure.Connection
    )
