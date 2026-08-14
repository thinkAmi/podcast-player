package dev.thinkami.podcastplayer.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 番組アートワークの表示サイズ。設定画面は作らないので、変えたければここを書き換えてビルドする。
 *
 * デコードの目標ピクセルは表示 dp に端末の密度(Pixel 7 Pro は約 3.5)を掛けて切り上げたもの。 密度を実行時に読んで厳密に合わせることもできるが、ViewModel が
 * Compose の Density を知る必要が生じる。 数枚の画像のために層をまたぐ配線を増やす価値はない。
 */
object ArtworkSizes {
    /** 購読一覧の行。 */
    val THUMBNAIL: Dp = 56.dp

    const val THUMBNAIL_TARGET_PX = 224

    /** エピソード一覧の上部バー。 */
    val HEADER: Dp = 40.dp

    const val HEADER_TARGET_PX = 160

    /** プレイヤー画面。画面幅いっぱいまで拡げず、余白を残す。 */
    val PLAYER: Dp = 280.dp

    const val PLAYER_TARGET_PX = 1024
}
