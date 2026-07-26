package dev.thinkami.podcastplayer.data.db

import androidx.room.Embedded
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem

/** 購読一覧クエリの受け取り用。data/db の内部に閉じ、外へは SubscriptionListItem として出す。 */
data class FeedWithUnplayedCount(
    @Embedded val feed: FeedEntity,
    val unplayedCount: Int,
)

fun FeedWithUnplayedCount.toModel(): SubscriptionListItem =
    SubscriptionListItem(feed = feed.toModel(), unplayedCount = unplayedCount)
