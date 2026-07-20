package dev.thinkami.podcastplayer.data.net

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTP取得。OS標準の [HttpURLConnection] だけを使い、OkHttp等は導入しない。
 *
 * 通信は必ず利用者の操作を起点として呼ばれる。ここに自動リトライや定期実行を実装しないこと。
 */
class HttpFetcher {

    /** フィードXMLのような小さなテキストを取得する。 */
    suspend fun fetchText(url: String): String =
        withContext(Dispatchers.IO) {
            openStream(url).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        }

    /**
     * 音声ファイルのような大きなデータを、進捗を通知しながら読み出す。
     *
     * 受け取り側([consume])がストリームを書き出す。ここでファイルを作らないのは、保存先の 決定をこのクラスの責務にしないため。
     */
    suspend fun <T> fetchStream(url: String, consume: (InputStream, Long) -> T): T =
        withContext(Dispatchers.IO) {
            val connection = openConnection(url)
            try {
                val contentLength = connection.contentLengthLong
                connection.inputStream.use { stream -> consume(stream, contentLength) }
            } finally {
                connection.disconnect()
            }
        }

    private fun openStream(url: String): InputStream {
        val connection = openConnection(url)
        return ClosingInputStream(connection.inputStream, connection)
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
        connection.connect()
        val status = connection.responseCode
        if (status !in HTTP_OK_RANGE) {
            connection.disconnect()
            throw IOException("HTTP $status: $url")
        }
        return connection
    }

    /** ストリームを閉じたときに接続も解放するためのラッパー。 */
    private class ClosingInputStream(
        private val delegate: InputStream,
        private val connection: HttpURLConnection,
    ) : InputStream() {
        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

        override fun available(): Int = delegate.available()

        override fun close() {
            try {
                delegate.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val USER_AGENT = "podcast-player/0.1 (personal use)"
        private val HTTP_OK_RANGE = 200..299
    }
}
