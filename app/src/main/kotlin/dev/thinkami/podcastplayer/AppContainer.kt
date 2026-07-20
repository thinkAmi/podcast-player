package dev.thinkami.podcastplayer

import android.content.Context
import dev.thinkami.podcastplayer.data.db.PodcastDatabase

/**
 * 手動DIコンテナ。アプリの生存期間中ひとつだけ存在する。
 *
 * 依存が増えたらここに `by lazy` を足していく。フレームワークを導入する前に、まず 「本当に必要な依存か」を疑うこと。
 */
class AppContainer(private val applicationContext: Context) {

    val database: PodcastDatabase by lazy { PodcastDatabase.create(applicationContext) }
}
