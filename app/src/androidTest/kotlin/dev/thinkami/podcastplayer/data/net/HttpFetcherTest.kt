package dev.thinkami.podcastplayer.data.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [HttpFetcher] の計装テスト。
 *
 * 実機で走らせる必要がある。AndroidのHttpURLConnectionは中身がOkHttpで透過gzipを行うのに対し、
 * JVMのそれは素のJDK実装で透過gzipを行わない。JVMユニットテストで書くと本番と別の実装を検証してしまう。
 */
@RunWith(AndroidJUnit4::class)
class HttpFetcherTest {

    private lateinit var server: FakeHttpServer
    private val fetcher = HttpFetcher()

    @Before
    fun setUp() {
        server = FakeHttpServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun gzipで返ってきたフィードは解凍済みのテキストとして受け取れる() {
        val xml = "<rss><channel><title>テスト番組</title></channel></rss>"
        server.respondWith(FakeResponse.gzipped(xml))

        val fetched = runBlocking { fetcher.fetchText(server.url("/feed.xml")) }

        assertEquals(xml, fetched)
    }

    @Test
    fun テキスト取得はAccept_Encodingを自分で指定せずOSに任せる() {
        server.respondWith(FakeResponse.plainText("<rss/>"))

        runBlocking { fetcher.fetchText(server.url("/feed.xml")) }

        // 自分で指定するとOSの透過解凍が無効になる。OSが付けたgzip要求が届いていること。
        val accepted = server.requests.single().header("Accept-Encoding")
        assertTrue("Accept-Encoding=$accepted", accepted!!.contains("gzip", ignoreCase = true))
    }

    @Test
    fun ストリーム取得はidentityを明示して圧縮を要求しない() {
        server.respondWith(FakeResponse.binary(AUDIO))

        runBlocking {
            fetcher.fetchStream(server.url("/ep.mp3")) { stream, _ -> stream.readBytes() }
        }

        assertEquals("identity", server.requests.single().header("Accept-Encoding"))
    }

    @Test
    fun ストリーム取得では実サイズのContent_Lengthが渡る() {
        server.respondWith(FakeResponse.binary(AUDIO))

        val (bytes, contentLength) =
            runBlocking {
                fetcher.fetchStream(server.url("/ep.mp3")) { stream, length ->
                    stream.readBytes() to length
                }
            }

        assertArrayEquals(AUDIO, bytes)
        assertEquals(AUDIO.size.toLong(), contentLength)
    }

    @Test
    fun identityを無視して圧縮された応答は保存せず失敗させる() {
        server.respondWith(FakeResponse.gzippedBinary(AUDIO))

        assertThrows(CompressedResponseException::class.java) {
            runBlocking {
                fetcher.fetchStream(server.url("/ep.mp3")) { stream, _ -> stream.readBytes() }
            }
        }
    }

    @Test
    fun fileスキームは接続前に拒否される() {
        // ClassCastException ではなく IOException 系で落ちることが要点。
        // 前者は上位の catch を素通りしてアプリが落ちる。
        val thrown =
            assertThrows(IOException::class.java) {
                runBlocking { fetcher.fetchText("file:///sdcard/feed.xml") }
            }

        assertTrue(thrown is UnsupportedUrlException)
    }

    @Test
    fun loopback宛の平文HTTPは書き換えられず届く() {
        // 計装テストの Fake サーバーは平文。ここが https へ書き換わると接続できなくなる。
        server.respondWith(FakeResponse.plainText("<rss/>"))

        val fetched = runBlocking { fetcher.fetchText(server.url("/feed.xml")) }

        assertEquals("<rss/>", fetched)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun loopback以外の平文HTTPはhttpsとして取りにいく() {
        // 同じ待ち受けを loopback 以外の名前で指す。書き換わるので https で接続を試みることになり、
        // 平文で待つサーバーとは手が合わずに失敗する。
        val plainUrl = server.url("/feed.xml").replaceFirst(LOOPBACK_IP, "localhost")

        assertThrows(IOException::class.java) { runBlocking { fetcher.fetchText(plainUrl) } }

        // 書き換えが効いていなければ、平文のGETがそのまま届いてしまう。
        // TLSの握手はこのサーバーからは解釈できない断片として見えるので、パスの有無で判定する。
        assertTrue(
            server.requests.map { it.requestLine }.toString(),
            server.requests.none { it.requestLine.contains("/feed.xml") },
        )
    }

    @Test
    fun 許可外スキームのストリーム取得も拒否される() {
        assertThrows(UnsupportedUrlException::class.java) {
            runBlocking {
                fetcher.fetchStream("file:///sdcard/ep.mp3") { stream, _ -> stream.readBytes() }
            }
        }
    }

    @Test
    fun エラー応答はステータス付きのIOExceptionになる() {
        server.respondWith(FakeResponse.status(NOT_FOUND, "Not Found"))

        // ステータスを型で持つ。行に出す失敗の種別を、呼び出し側がメッセージ文字列の
        // 解釈なしに決められるようにするため。
        val thrown =
            assertThrows(HttpStatusException::class.java) {
                runBlocking { fetcher.fetchText(server.url("/missing.xml")) }
            }

        assertTrue(thrown is IOException)
        assertEquals(NOT_FOUND, thrown.status)
    }

    private companion object {
        val AUDIO = ByteArray(2_048) { (it % 251).toByte() }
        const val NOT_FOUND = 404
        const val LOOPBACK_IP = "127.0.0.1"
    }
}
