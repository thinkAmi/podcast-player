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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * エピソード音声のダウンロード。
 *
 * 自動ダウンロードは実装しない。DLは必ず利用者が行アイコンをタップした結果として起きる。 自動リトライもしない(失敗は行に表示し、再タップでやり直す)。
 *
 * DLの実行はアプリ生存期間の [scope] が引き受ける。依頼した画面(ViewModel)のスコープで走らせると、
 * 受信の途中で画面を閉じたときにキャンセルされ、受信し終えたのに保存が確定しないまま進捗だけが残る。
 */
class EpisodeDownloader(
    private val fetcher: HttpFetcher,
    private val storage: MediaFileStorage,
    private val episodeDao: EpisodeDao,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val mutableStates = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())

    /** 実行中・失敗の状態。DL済みかどうかはDBが持つ。 */
    val states: StateFlow<Map<Long, DownloadState>> = mutableStates.asStateFlow()

    /** 1件のDLを開始する。完了を待たずに戻り、進み具合は [states] とDBに現れる。 */
    fun start(episodeId: Long) {
        scope.launch { download(episodeId) }
    }

    /**
     * 1件をダウンロードして保存し、DBのDL状態を更新する。
     *
     * 途中で失敗した場合は一時ファイルを片付け、DB上は未DLのままにする。中途半端なファイルが DL済みとして残ると、再生時に壊れた音声を掴むことになるため。
     */
    private suspend fun download(episodeId: Long) {
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
        } finally {
            abandonIfUnfinished(episodeId, partial)
        }
    }

    /**
     * 完了も失敗もしないまま抜けた(キャンセル・想定外の例外)ときの片付け。
     *
     * 例外そのものは握らずに伝播させ、一時ファイルと進行中の表示だけを消す。進行中の表示が残ると 再タップも受け付けなくなり、アプリを終了するまで取り戻せないため。
     */
    private fun abandonIfUnfinished(episodeId: Long, partial: File) {
        if (mutableStates.value[episodeId] is DownloadState.InProgress) {
            storage.delete(partial.absolutePath)
            updateState(episodeId, DownloadState.Idle)
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

    /** 複数のDLが別スレッドから進捗を書き込むため、読み直して書く操作は不可分にする。 */
    private fun updateState(episodeId: Long, state: DownloadState) {
        mutableStates.update { states ->
            if (state is DownloadState.Idle) {
                states - episodeId
            } else {
                states + (episodeId to state)
            }
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
