package dev.thinkami.podcastplayer.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.thinkami.podcastplayer.logic.model.SubscriptionListItem

/**
 * 購読一覧の番組1行。タイトル・フィードURLに加え、行末に未聴数のバッジを出す。
 *
 * バッジは Material3 の Badge ではなく自前のピル。Badge のデフォルトは error 色(赤)で 「通知・警告」の記号論を背負うが、この数字は通知ではなく在庫数なので
 * secondaryContainer 系の 落ち着いた色にする。0件ならピル自体を描画しない(有無のコントラストが「新着の有無」を伝える)。 数字は丸めない(347 は
 * 347。数の大きさ自体が「すべて視聴済みで潰す」操作のトリガー情報)。
 */
@Composable
fun FeedRow(item: SubscriptionListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = item.feed.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                text = item.feed.feedUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (item.unplayedCount > 0) {
            UnplayedCountPill(count = item.unplayedCount)
        }
    }
}

@Composable
private fun UnplayedCountPill(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
