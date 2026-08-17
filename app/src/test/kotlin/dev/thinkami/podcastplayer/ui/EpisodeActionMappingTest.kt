package dev.thinkami.podcastplayer.ui

import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.DownloadFailure
import dev.thinkami.podcastplayer.logic.EpisodeAction
import dev.thinkami.podcastplayer.logic.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 一覧の行と統合エピソード画面が共有する橋渡しの検査。
 *
 * 判断そのものは logic 側で全数列挙している。ここで確かめるのは「画面が持っている型を 判断の入力へ正しく写しているか」だけ — 取り違えるとどちらの画面も同時に壊れる箇所。
 */
class EpisodeActionMappingTest {

    private fun episode(downloaded: Boolean = false) =
        Episode(
            id = 1L,
            feedId = 1L,
            guid = "g",
            title = "テストエピソード",
            showNotes = null,
            publishedAtEpochMillis = 0L,
            durationMs = null,
            enclosureUrl = "https://example.test/a.mp3",
            enclosureSizeBytes = null,
            played = false,
            downloaded = downloaded,
            localPath = if (downloaded) "/tmp/a.mp3" else null,
            positionMs = 0L,
            favorite = false,
        )

    @Test
    fun `DL実行中は進捗を出す`() {
        val action =
            episodeActionFor(
                episode = episode(),
                downloadState = DownloadState.InProgress(bytesRead = 1L, totalBytes = 2L),
                isCurrent = false,
            )
        assertEquals(EpisodeAction.DOWNLOADING, action)
    }

    @Test
    fun `DL失敗は再試行になる`() {
        val action =
            episodeActionFor(
                episode = episode(),
                downloadState = DownloadState.Failed(DownloadFailure.Connection),
                isCurrent = false,
            )
        assertEquals(EpisodeAction.RETRY_DOWNLOAD, action)
    }

    @Test
    fun `Idleの未DLはダウンロード`() {
        val action =
            episodeActionFor(
                episode = episode(),
                downloadState = DownloadState.Idle,
                isCurrent = false,
            )
        assertEquals(EpisodeAction.DOWNLOAD, action)
    }

    @Test
    fun `状態が無いDL済みは再生`() {
        val action =
            episodeActionFor(
                episode = episode(downloaded = true),
                downloadState = null,
                isCurrent = false,
            )
        assertEquals(EpisodeAction.PLAY, action)
    }

    @Test
    fun `現在のエピソードはトグル`() {
        val action =
            episodeActionFor(
                episode = episode(downloaded = true),
                downloadState = null,
                isCurrent = true,
            )
        assertEquals(EpisodeAction.TOGGLE_PLAY_PAUSE, action)
    }

    @Test
    fun `失敗した操作の名前は種別から決まる`() {
        assertEquals(
            "DL失敗(HTTP 404)。もう一度試す",
            DownloadState.Failed(DownloadFailure.HttpStatus(404)).failureActionLabel(),
        )
        assertEquals(
            "DL失敗(取得できないURL)",
            DownloadState.Failed(DownloadFailure.UnsupportedUrl).failureActionLabel(),
        )
    }

    @Test
    fun `種別が読めないときは接続不能として扱う`() {
        // RETRY_DOWNLOAD は Failed のときしか現れないが、型では保証できない。
        // 案内を出す側に倒しても操作は妨げない。
        assertEquals("DL失敗(接続できず)。もう一度試す", null.failureActionLabel())
    }
}
