package dev.thinkami.podcastplayer.data.storage

import android.content.Context
import java.io.File

/**
 * 端末上のファイル配置。
 *
 * 保存先は外部ストレージのアプリ専用領域。ランタイム権限が不要で、アンインストール時に 自動で片付く。他アプリから見える必要はない(音声を他の音楽アプリで鳴らす想定はない)。
 */
class MediaFileStorage(private val context: Context) {

    private val episodesDir: File
        get() = File(context.getExternalFilesDir(null), DIR_EPISODES).apply { mkdirs() }

    private val artworkDir: File
        get() = File(context.getExternalFilesDir(null), DIR_ARTWORK).apply { mkdirs() }

    fun episodeFile(episodeId: Long): File = File(episodesDir, "$episodeId.mp3")

    fun artworkFile(feedId: Long): File = File(artworkDir, "$feedId.img")

    /** 削除できたかどうかを返す。存在しなければ何もしない。 */
    fun delete(path: String?): Boolean {
        val file = path?.let(::File) ?: return false
        return !file.exists() || file.delete()
    }

    private companion object {
        const val DIR_EPISODES = "episodes"
        const val DIR_ARTWORK = "artwork"
    }
}
