package dev.thinkami.podcastplayer.ui.subscriptions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import dev.thinkami.podcastplayer.logic.model.Feed
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 購読一覧の行のスモークテスト。
 *
 * 「0件ならバッジ自体を出さない」「アートワークが無ければ頭文字を出す」の分岐は UI の if にしか 現れないため、この層でのみ検証できる。
 */
@RunWith(AndroidJUnit4::class)
class FeedRowTest {

    @get:Rule val composeRule = createComposeRule()

    private fun item(unplayedCount: Int) =
        SubscriptionListItem(
            feed =
                Feed(
                    id = 1L,
                    feedUrl = "https://example.test/feed.xml",
                    title = "テスト番組",
                    artworkUrl = null,
                    artworkLocalPath = null,
                    filter = EpisodeFilter(unplayedOnly = false, downloadedOnly = false),
                    playbackSpeed = Feed.DEFAULT_PLAYBACK_SPEED,
                ),
            unplayedCount = unplayedCount,
        )

    @Test
    fun 未聴があれば数字がそのまま表示される() {
        composeRule.setContent {
            FeedRow(item = item(unplayedCount = 347), artwork = null, onClick = {})
        }

        composeRule.onNodeWithText("347").assertIsDisplayed()
        composeRule.onNodeWithText("テスト番組").assertIsDisplayed()
    }

    @Test
    fun 未聴0件ならバッジが存在しない() {
        composeRule.setContent {
            FeedRow(item = item(unplayedCount = 0), artwork = null, onClick = {})
        }

        composeRule.onNodeWithText("テスト番組").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertDoesNotExist()
    }

    /** アートワークを取得できない番組はふつうに存在する。エラーではなく頭文字のタイルに落ちる。 */
    @Test
    fun アートワークがなければ頭文字のタイルを出す() {
        composeRule.setContent {
            FeedRow(item = item(unplayedCount = 1), artwork = null, onClick = {})
        }

        composeRule.onNodeWithText("テ").assertIsDisplayed()
    }

    /** フィードURLは購読一覧には出さない(エピソード一覧の上部バーへ移してある)。 */
    @Test
    fun フィードURLは行に表示しない() {
        composeRule.setContent {
            FeedRow(item = item(unplayedCount = 1), artwork = null, onClick = {})
        }

        composeRule.onNodeWithText("https://example.test/feed.xml").assertDoesNotExist()
    }
}
