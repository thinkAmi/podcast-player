package dev.thinkami.podcastplayer

import android.app.Application
import android.content.Context

/**
 * 依存の組み立てはここだけで行う。
 *
 * DIフレームワークは使わない。この規模では依存の数が数個で、フレームワークが解決する問題 (組み立ての自動化、スコープ管理)がそもそも発生しないため。
 */
class PodcastPlayerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Application から依存を取り出すための拡張。 */
val Context.appContainer: AppContainer
    get() = (applicationContext as PodcastPlayerApplication).container
