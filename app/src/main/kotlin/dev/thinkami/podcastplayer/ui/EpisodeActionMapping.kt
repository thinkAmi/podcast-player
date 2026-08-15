package dev.thinkami.podcastplayer.ui

import dev.thinkami.podcastplayer.data.download.DownloadState
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
