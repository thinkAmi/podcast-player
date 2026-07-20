package dev.thinkami.podcastplayer.ui.episodes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.data.download.DownloadState
import dev.thinkami.podcastplayer.logic.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 行の3つのタップ領域が意図どおりに割り当たっているかのスモークテスト。
 *
 * この行はアプリの操作の中心(再生・DL・視聴済み・詳細のすべての入口)なので、 取り違えが起きていないことだけは機械的に確かめておく。
 */
@RunWith(AndroidJUnit4::class)
class EpisodeRowTest {

    @get:Rule val composeRule = createComposeRule()

    private fun episode(played: Boolean = false, downloaded: Boolean = false) =
        Episode(
            id = 1L,
            feedId = 1L,
            guid = "g",
            title = "テストエピソード",
            showNotes = null,
            publishedAtEpochMillis = 0L,
            durationMs = 3_600_000L,
            enclosureUrl = "https://example.test/a.mp3",
            enclosureSizeBytes = null,
            played = played,
            downloaded = downloaded,
            localPath = if (downloaded) "/tmp/a.mp3" else null,
            positionMs = 0L,
            favorite = false,
        )

    @Test
    fun 未DLならダウンロードアイコンが出て再生アイコンは出ない() {
        composeRule.setContent {
            EpisodeRow(
                episode = episode(downloaded = false),
                downloadState = null,
                onPlay = {},
                onDownload = {},
                onTogglePlayed = {},
                onOpenDetail = {},
            )
        }

        composeRule.onNodeWithContentDescription("ダウンロード").assertIsDisplayed()
        composeRule.onNodeWithText("テストエピソード").assertIsDisplayed()
    }

    @Test
    fun DL済みなら再生アイコンが出る() {
        composeRule.setContent {
            EpisodeRow(
                episode = episode(downloaded = true),
                downloadState = null,
                onPlay = {},
                onDownload = {},
                onTogglePlayed = {},
                onOpenDetail = {},
            )
        }

        composeRule.onNodeWithContentDescription("再生").assertIsDisplayed()
    }

    @Test
    fun 各タップ領域が対応する操作を呼ぶ() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            EpisodeRow(
                episode = episode(downloaded = true),
                downloadState = null,
                onPlay = { calls += "play" },
                onDownload = { calls += "download" },
                onTogglePlayed = { calls += "played" },
                onOpenDetail = { calls += "detail" },
            )
        }

        composeRule.onNodeWithContentDescription("再生").performClick()
        composeRule.onNodeWithContentDescription("視聴済みにする").performClick()
        composeRule.onNodeWithText("テストエピソード").performClick()

        assertEquals(listOf("play", "played", "detail"), calls)
    }

    @Test
    fun 視聴済みなら未聴に戻す操作が出る() {
        composeRule.setContent {
            EpisodeRow(
                episode = episode(played = true, downloaded = true),
                downloadState = null,
                onPlay = {},
                onDownload = {},
                onTogglePlayed = {},
                onOpenDetail = {},
            )
        }

        composeRule.onNodeWithContentDescription("未聴に戻す").assertIsDisplayed()
    }

    @Test
    fun DL失敗なら再試行できる表示になる() {
        var retried = false
        composeRule.setContent {
            EpisodeRow(
                episode = episode(downloaded = false),
                downloadState = DownloadState.Failed("失敗"),
                onPlay = {},
                onDownload = { retried = true },
                onTogglePlayed = {},
                onOpenDetail = {},
            )
        }

        composeRule.onNodeWithContentDescription("ダウンロードに失敗。もう一度試す").performClick()

        assertTrue(retried)
    }
}
