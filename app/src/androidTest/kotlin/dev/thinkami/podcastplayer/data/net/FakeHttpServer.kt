package dev.thinkami.podcastplayer.data.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * 計装テスト用の最小HTTPサーバー。
 *
 * MockWebServer(Square製)は導入せず、OS標準の [ServerSocket] だけで書く。検証したいのは
 * 「アプリがどんなリクエストを送るか」と「特定の応答をどう扱うか」の2点だけなので、これで足りる。
 *
 * 端末内の loopback だけに bind し、ポートはOSに割り当てさせる。外部ネットワークに依存しないため 機内モードでも動き、テストは決定論的になる。
 */
class FakeHttpServer : AutoCloseable {

    private val serverSocket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
    private val recorded = CopyOnWriteArrayList<RecordedRequest>()

    @Volatile private var response: FakeResponse = FakeResponse.plainText("")

    private val worker = thread(isDaemon = true, name = "FakeHttpServer") { acceptLoop() }

    /** 受信したリクエスト。アプリが送ったヘッダの検証に使う。 */
    val requests: List<RecordedRequest>
        get() = recorded.toList()

    fun url(path: String): String = "http://$LOOPBACK:${serverSocket.localPort}$path"

    fun respondWith(response: FakeResponse) {
        this.response = response
    }

    override fun close() {
        serverSocket.close()
        worker.join(JOIN_TIMEOUT_MS)
    }

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val client =
                try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    // close() による停止だけを正常終了として扱う。それ以外は握りつぶさない。
                    if (!serverSocket.isClosed) throw e
                    return
                }
            client.use(::handle)
        }
    }

    private fun handle(client: Socket) {
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine()
            if (line.isNullOrEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.take(separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        recorded += RecordedRequest(requestLine, headers)
        writeResponse(client, response)
    }

    private fun writeResponse(client: Socket, response: FakeResponse) {
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${response.reason}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            response.contentEncoding?.let { append("Content-Encoding: $it\r\n") }
            append("Connection: close\r\n")
            append("\r\n")
        }
        val output = client.getOutputStream()
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(response.body)
        output.flush()
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val BACKLOG = 4
        const val JOIN_TIMEOUT_MS = 2_000L
    }
}

/** 受信したリクエスト。ヘッダ名は小文字に正規化してある。 */
class RecordedRequest(val requestLine: String, val headers: Map<String, String>) {
    fun header(name: String): String? = headers[name.lowercase()]
}

/** 返す応答。[contentEncoding] が null なら Content-Encoding ヘッダを付けない。 */
class FakeResponse(
    val status: Int,
    val reason: String,
    val body: ByteArray,
    val contentEncoding: String?,
) {
    companion object {
        private const val HTTP_OK = 200
        private const val REASON_OK = "OK"

        fun plainText(body: String): FakeResponse =
            FakeResponse(HTTP_OK, REASON_OK, body.toByteArray(Charsets.UTF_8), null)

        fun binary(body: ByteArray): FakeResponse = FakeResponse(HTTP_OK, REASON_OK, body, null)

        /** gzip圧縮して `Content-Encoding: gzip` を付けて返す。 */
        fun gzipped(body: String): FakeResponse =
            FakeResponse(HTTP_OK, REASON_OK, gzip(body.toByteArray(Charsets.UTF_8)), "gzip")

        /** gzip圧縮したバイト列を返す。音声の代わりに使う。 */
        fun gzippedBinary(body: ByteArray): FakeResponse =
            FakeResponse(HTTP_OK, REASON_OK, gzip(body), "gzip")

        fun status(status: Int, reason: String): FakeResponse =
            FakeResponse(status, reason, ByteArray(0), null)

        private fun gzip(raw: ByteArray): ByteArray {
            val buffer = ByteArrayOutputStream()
            GZIPOutputStream(buffer).use { it.write(raw) }
            return buffer.toByteArray()
        }
    }
}
