package dev.thinkami.podcastplayer.data.download

import dev.thinkami.podcastplayer.data.db.EpisodeDao
import dev.thinkami.podcastplayer.data.net.CompressedResponseException
import dev.thinkami.podcastplayer.data.net.HttpFetcher
import dev.thinkami.podcastplayer.data.net.HttpStatusException
import dev.thinkami.podcastplayer.data.net.UnsupportedUrlException
import dev.thinkami.podcastplayer.data.storage.MediaFileStorage
import dev.thinkami.podcastplayer.logic.DownloadFailure
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * エピソード音声のダウンロード。
 *
 * 自動ダウンロードは実装しない。DLは必ず利用者が行アイコンをタップした結果として起きる。 自動リトライもしない(失敗は行に表示し、再タップでやり直す)。
 */
class EpisodeDownloader(
    private val fetcher: HttpFetcher,
    private val storage: MediaFileStorage,
    private val episodeDao: EpisodeDao,
) {

    private val mutableStates = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())

    /** 実行中・失敗の状態。DL済みかどうかはDBが持つ。 */
    val states: StateFlow<Map<Long, DownloadState>> = mutableStates.asStateFlow()

    /**
     * 1件をダウンロードして保存し、DBのDL状態を更新する。
     *
     * 途中で失敗した場合は一時ファイルを片付け、DB上は未DLのままにする。中途半端なファイルが DL済みとして残ると、再生時に壊れた音声を掴むことになるため。
     */
    suspend fun download(episodeId: Long) {
        val episode = episodeDao.findById(episodeId) ?: return
        if (mutableStates.value[episodeId] is DownloadState.InProgress) return

        val target = storage.episodeFile(episodeId)
        val partial = File("${target.absolutePath}$PARTIAL_SUFFIX")
        updateState(episodeId, DownloadState.InProgress(0L, episode.enclosureSizeBytes ?: 0L))

        try {
            fetcher.fetchStream(episode.enclosureUrl) { stream, contentLength ->
                writeWithProgress(episodeId, stream, partial, contentLength)
            }
            finalize(episodeId, partial, target)
        } catch (e: IOException) {
            storage.delete(partial.absolutePath)
            updateState(episodeId, DownloadState.Failed(classify(e), e.message))
        }
    }

    /**
     * 例外を行に出せる種別へ分類する。
     *
     * 分類がここにあるのは、例外型という JVM の都合を知っているのが data 層だから。 種別から先(文言・再試行を勧めるか)の判断は logic 層が持つ。
     */
    private fun classify(e: IOException): DownloadFailure =
        when (e) {
            is UnsupportedUrlException -> DownloadFailure.UnsupportedUrl
            is HttpStatusException -> DownloadFailure.HttpStatus(e.status)
            is CompressedResponseException -> DownloadFailure.CompressedResponse
            else -> DownloadFailure.Connection
        }

    private fun writeWithProgress(
        episodeId: Long,
        stream: InputStream,
        partial: File,
        contentLength: Long,
    ) {
        var read = 0L
        partial.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var count = stream.read(buffer)
            while (count >= 0) {
                output.write(buffer, 0, count)
                read += count
                updateState(episodeId, DownloadState.InProgress(read, contentLength))
                count = stream.read(buffer)
            }
        }
    }

    /** 完了して初めて本来の名前にする。DL済み=再生可能、を保証するため。 */
    private suspend fun finalize(episodeId: Long, partial: File, target: File) {
        storage.delete(target.absolutePath)
        if (!partial.renameTo(target)) {
            storage.delete(partial.absolutePath)
            updateState(episodeId, DownloadState.Failed(DownloadFailure.Save))
            return
        }
        episodeDao.setDownloadState(episodeId, downloaded = true, localPath = target.absolutePath)
        updateState(episodeId, DownloadState.Idle)
    }

    private fun updateState(episodeId: Long, state: DownloadState) {
        mutableStates.value =
            if (state is DownloadState.Idle) {
                mutableStates.value - episodeId
            } else {
                mutableStates.value + (episodeId to state)
            }
    }

    /** 失敗表示を消す(再試行時に呼ぶ)。 */
    fun clearFailure(episodeId: Long) {
        if (mutableStates.value[episodeId] is DownloadState.Failed) {
            updateState(episodeId, DownloadState.Idle)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PARTIAL_SUFFIX = ".part"
    }
}
