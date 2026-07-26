package dev.thinkami.podcastplayer.data.net

import dev.thinkami.podcastplayer.logic.HttpUrlPolicy
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
 *
 * 取得URLの検査はこのクラスに集約する。購読URL・フィード内の artworkUrl / enclosureUrl という3つの
 * 入口それぞれで検査するのではなく、通信の唯一の出口であるここで強制する。
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
            val connection = connect(url, acceptEncoding = ENCODING_IDENTITY)
            try {
                requireNotCompressed(connection, url)
                val contentLength = connection.contentLengthLong
                connection.inputStream.use { stream -> consume(stream, contentLength) }
            } finally {
                connection.disconnect()
            }
        }

    /**
     * テキスト取得は `Accept-Encoding` を指定しない。
     *
     * 自分で指定するとOSの透過gzip(自動要求・自動解凍)が無効になり、gzipで返ってきた応答を 解凍しないまま文字列化してしまう。指定しなければOSが要求と解凍の両方を行うため、
     * 転送量の削減(テキストは1/5〜1/10になる)を保ったまま解凍漏れが構造的に起きなくなる。
     */
    private fun openStream(url: String): InputStream {
        val connection = connect(url, acceptEncoding = null)
        return ClosingInputStream(connection.inputStream, connection)
    }

    private fun connect(url: String, acceptEncoding: String?): HttpURLConnection {
        // キャスト前に検査する。file: などを渡すと ClassCastException になり、IOException では
        // ないため上位の catch を素通りしてアプリが落ちる。
        if (!HttpUrlPolicy.isAllowed(url)) {
            throw UnsupportedUrlException(url)
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        acceptEncoding?.let { connection.setRequestProperty(HEADER_ACCEPT_ENCODING, it) }
        connection.connect()
        val status = connection.responseCode
        if (status !in HTTP_OK_RANGE) {
            connection.disconnect()
            throw IOException("HTTP $status: $url")
        }
        return connection
    }

    /**
     * 圧縮されていない応答であることを確かめる。
     *
     * 音声や画像は既に圧縮済みでgzipが効かず、配信側もRangeリクエストとの両立のため圧縮しない。 こちらは `identity`
     * を明示しているので、それでも圧縮して返すサーバーは非準拠。 黙って保存すると壊れたファイルがDL済みとして残るため、失敗させる。
     */
    private fun requireNotCompressed(connection: HttpURLConnection, url: String) {
        val encoding = connection.contentEncoding?.trim()
        if (!encoding.isNullOrEmpty() && !encoding.equals(ENCODING_IDENTITY, ignoreCase = true)) {
            connection.disconnect()
            throw CompressedResponseException(url, encoding)
        }
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
        private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        private const val ENCODING_IDENTITY = "identity"
        private val HTTP_OK_RANGE = 200..299
    }
}

/** 取得を許可していないURL。https と計装テスト用の loopback 以外はここで止まる。 */
class UnsupportedUrlException(url: String) : IOException("取得できないURLです: $url")

/** `identity` を要求したにもかかわらず圧縮して返された応答。 */
class CompressedResponseException(url: String, encoding: String) :
    IOException("圧縮された応答には対応していません($encoding): $url")
