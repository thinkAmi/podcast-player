package dev.thinkami.podcastplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thinkami.podcastplayer.logic.ArtworkPresentation

/**
 * アートワーク1枚。画像が無ければ番組名の頭文字タイルを描く。
 *
 * 画像を取得できない番組は設計上ふつうに存在する(RSS が URL を申告していない・取得に失敗し続けて
 * いる・キャッシュが壊れている)。どれも聴取に支障はないので、エラー表示ではなく代替の見た目に落とす。
 *
 * 隣にはかならず番組名のテキストが並ぶため、読み上げ対象としては装飾扱い(contentDescription = null)。
 */
@Composable
fun ArtworkImage(
    bitmap: Bitmap?,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (bitmap == null) {
        MonogramTile(title = title, size = size, shape = shape, modifier = modifier)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    }
}

@Composable
private fun MonogramTile(
    title: String,
    size: Dp,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ArtworkPresentation.monogramFor(title),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = monogramFontSize(size),
            maxLines = 1,
        )
    }
}

/** タイルの大きさに対する文字の比率。小さなサムネイルでも大きなプレイヤーでも同じ見え方にする。 */
private fun monogramFontSize(size: Dp): TextUnit = (size.value * 0.45f).sp
