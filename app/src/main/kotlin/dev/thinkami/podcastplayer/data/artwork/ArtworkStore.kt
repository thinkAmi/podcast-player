package dev.thinkami.podcastplayer.data.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.ArtworkPresentation
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 番組アートワークの取得とキャッシュ。
 *
 * 画像ライブラリ(Coil等)は導入せず、BitmapFactory とファイルキャッシュだけで済ませる。 表示する画像は番組数ぶん(数枚〜十数枚)しかないため、凝った仕組みは要らない。
 *
 * 取得はフィード更新時にだけ行う。画面表示のたびに通信してはならない。
 */
class ArtworkStore(private val fetcher: HttpFetcher, private val storage: MediaFileStorage) {

    /**
     * まだキャッシュされていなければ取得して保存し、ローカルパスを返す。
     *
     * 取得に失敗してもフィード更新全体を失敗させない。アートワークは無くても聴取に支障がない。
     */
    suspend fun ensureCached(feedId: Long, artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            val file = storage.artworkFile(feedId)
            if (file.exists() && file.length() > 0L) {
                file.absolutePath
            } else {
                download(artworkUrl, file)
            }
        }
    }

    private suspend fun download(artworkUrl: String, file: File): String =
        try {
            fetcher.fetchStream(artworkUrl) { stream, _ ->
                file.outputStream().use { output -> stream.copyTo(output) }
            }
            file.absolutePath
        } catch (e: IOException) {
            // 取得できなければアートワークなしで運用する。原因は握りつぶさず呼び出し側へ返す。
            storage.delete(file.absolutePath)
            throw ArtworkUnavailableException(artworkUrl, e)
        }

    /**
     * キャッシュ済みの画像を表示用の大きさで読み込む。無い・壊れていれば null。
     *
     * 配信されるアートワークは 3000px 角も珍しくなく、原寸で展開すると1枚 36MB になる。 表示に必要なのは targetPx までなので、寸法だけ先に読んで縮小デコードする。
     *
     * null は異常ではなく通常の分岐(URL 未申告・取得失敗・キャッシュ破損)。呼び出し側は モノグラム表示へ落ちる。
     */
    suspend fun load(localPath: String?, targetPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (localPath == null) return@withContext null
            val bounds =
                BitmapFactory.Options()
                    .apply { inJustDecodeBounds = true }
                    .also { options ->
                        BitmapFactory.decodeFile(localPath, options)
                    }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize =
                        ArtworkPresentation.sampleSizeFor(
                            sourceWidth = bounds.outWidth,
                            sourceHeight = bounds.outHeight,
                            targetPx = targetPx,
                        )
                }
            BitmapFactory.decodeFile(localPath, options)
        }
}

class ArtworkUnavailableException(url: String, cause: IOException) :
    IOException("アートワークを取得できませんでした: $url", cause)
