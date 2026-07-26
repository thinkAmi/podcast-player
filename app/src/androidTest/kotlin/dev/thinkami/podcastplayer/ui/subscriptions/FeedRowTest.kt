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
 * 未聴数バッジのスモークテスト。
 *
 * 「0件ならバッジ自体を出さない」の判定は UI の if にしか現れないため、この層でのみ検証できる。
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
        composeRule.setContent { FeedRow(item = item(unplayedCount = 347), onClick = {}) }

        composeRule.onNodeWithText("347").assertIsDisplayed()
        composeRule.onNodeWithText("テスト番組").assertIsDisplayed()
    }

    @Test
    fun 未聴0件ならバッジが存在しない() {
        composeRule.setContent { FeedRow(item = item(unplayedCount = 0), onClick = {}) }

        composeRule.onNodeWithText("テスト番組").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertDoesNotExist()
    }
}
