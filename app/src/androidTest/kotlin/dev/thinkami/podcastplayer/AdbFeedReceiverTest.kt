package dev.thinkami.podcastplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.thinkami.podcastplayer.data.net.FakeHttpServer
import dev.thinkami.podcastplayer.data.net.FakeResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AdbFeedReceiver] の計装テスト。
 *
 * 計装テストは対象アプリと同じ uid で走るため、`exported="false"` の Receiver へ実際に ordered broadcast を送れる。これは PC から
 * `run-as` で送るときと同じ経路であり、 結果コードの伝播まで含めて本番と同じものを検証できる。
 */
@RunWith(AndroidJUnit4::class)
class AdbFeedReceiverTest {

    private lateinit var server: FakeHttpServer
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        server = FakeHttpServer()
    }

    @After
    fun tearDown() {
        server.close()
        // 登録された番組を片付ける。計装テスト用アプリのDBであり本番には影響しないが、
        // テスト間で状態を持ち越さない。
        runBlocking {
            val repository = context.appContainer.feedRepository
            repository.observeFeeds().first().forEach { repository.unsubscribe(it.id) }
        }
    }

    @Test
    fun 有効なフィードURLを受け取ると購読が追加される() {
        server.respondWith(FakeResponse.plainText(FEED_XML))
        val feedUrl = server.url("/feed.xml")

        val result = broadcast(feedUrl)

        assertEquals(result.data, AdbFeedReceiver.RESULT_SUBSCRIBED, result.code)
        val feeds = runBlocking { context.appContainer.feedRepository.observeFeeds().first() }
        assertEquals(1, feeds.size)
        assertEquals("テスト番組", feeds.single().title)
        assertEquals(feedUrl, feeds.single().feedUrl)
    }

    @Test
    fun extraがなければ何もしない() {
        val result = broadcast(feedUrl = null)

        assertEquals(AdbFeedReceiver.RESULT_IGNORED, result.code)
        assertTrue(
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.isEmpty()
        )
    }

    @Test
    fun extraが空白だけなら何もしない() {
        val result = broadcast(feedUrl = "   ")

        assertEquals(AdbFeedReceiver.RESULT_IGNORED, result.code)
        assertTrue(
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.isEmpty()
        )
    }

    @Test
    fun 取得に失敗したら理由を返し購読は追加されない() {
        server.respondWith(FakeResponse.status(NOT_FOUND, "Not Found"))

        val result = broadcast(server.url("/missing.xml"))

        assertEquals(AdbFeedReceiver.RESULT_FAILED, result.code)
        assertNotNull(result.data)
        assertTrue(
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.isEmpty()
        )
    }

    @Test
    fun 許可外スキームのURLは購読されず理由が返る() {
        val result = broadcast("file:///sdcard/feed.xml")

        assertEquals(AdbFeedReceiver.RESULT_FAILED, result.code)
        assertTrue(
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.isEmpty()
        )
    }

    @Test
    fun 同じURLの二重登録は理由を返して拒否される() {
        server.respondWith(FakeResponse.plainText(FEED_XML))
        val feedUrl = server.url("/feed.xml")
        broadcast(feedUrl)

        val result = broadcast(feedUrl)

        assertEquals(AdbFeedReceiver.RESULT_FAILED, result.code)
        assertEquals(
            1,
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.size,
        )
    }

    @Test
    fun 出典の一致するXMLを既存購読に取り込める() {
        server.respondWith(FakeResponse.plainText(FEED_XML))
        val feedUrl = server.url("/feed.xml")
        broadcast(feedUrl)
        server.respondWith(FakeResponse.plainText(archiveXml(feedUrl)))

        val result = broadcast(feedUrl, importUrl = server.url("/archive.xml"))

        assertEquals(result.data, AdbFeedReceiver.RESULT_IMPORTED, result.code)
        val feeds = runBlocking { context.appContainer.feedRepository.observeFeeds().first() }
        // 取り込みで番組が増えたり、番組名が上書きされたりしないこと。
        assertEquals(1, feeds.size)
        assertEquals("テスト番組", feeds.single().title)
    }

    @Test
    fun 出典の一致しないXMLは取り込まれず理由が返る() {
        server.respondWith(FakeResponse.plainText(FEED_XML))
        val feedUrl = server.url("/feed.xml")
        broadcast(feedUrl)
        server.respondWith(FakeResponse.plainText(archiveXml("https://other.test/rss")))

        val result = broadcast(feedUrl, importUrl = server.url("/archive.xml"))

        assertEquals(AdbFeedReceiver.RESULT_FAILED, result.code)
        assertNotNull(result.data)
    }

    @Test
    fun 購読していないフィードへの取り込みは失敗する() {
        server.respondWith(FakeResponse.plainText(archiveXml("https://unknown.test/feed.xml")))

        val result =
            broadcast("https://unknown.test/feed.xml", importUrl = server.url("/archive.xml"))

        assertEquals(AdbFeedReceiver.RESULT_FAILED, result.code)
        assertTrue(
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.isEmpty()
        )
    }

    @Test
    fun 空白だけのimport_urlは従来どおり購読登録として扱う() {
        server.respondWith(FakeResponse.plainText(FEED_XML))
        val feedUrl = server.url("/feed.xml")

        val result = broadcast(feedUrl, importUrl = "   ")

        assertEquals(result.data, AdbFeedReceiver.RESULT_SUBSCRIBED, result.code)
        assertEquals(
            1,
            runBlocking { context.appContainer.feedRepository.observeFeeds().first() }.size,
        )
    }

    /** 実際に ordered broadcast を送り、Receiver が返した結果を受け取る。 */
    private fun broadcast(feedUrl: String?, importUrl: String? = null): Result {
        val intent =
            Intent(context, AdbFeedReceiver::class.java).apply {
                feedUrl?.let { putExtra(AdbFeedReceiver.EXTRA_FEED_URL, it) }
                importUrl?.let { putExtra(AdbFeedReceiver.EXTRA_IMPORT_URL, it) }
            }
        val latch = CountDownLatch(1)
        var code = UNSET
        var data: String? = null
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, received: Intent) {
                    code = resultCode
                    data = resultData
                    latch.countDown()
                }
            },
            null,
            UNSET,
            null,
            null,
        )
        assertTrue("結果が返らなかった", latch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        return Result(code, data)
    }

    private class Result(val code: Int, val data: String?)

    /** 取り込み用アーカイブ。[sourceUrl] が出典として申告される。 */
    private fun archiveXml(sourceUrl: String): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>テスト番組 過去回アーカイブ(非公式)</title>
            <item>
              <title>第0回</title>
              <guid>old-1</guid>
              <pubDate>Mon, 06 Feb 2017 09:00:00 +0900</pubDate>
              <enclosure url="https://example.test/old1.mp3" length="1000" />
              <source url="$sourceUrl">テスト番組</source>
            </item>
          </channel>
        </rss>
        """
            .trimIndent()

    private companion object {
        const val UNSET = 0
        const val NOT_FOUND = 404
        const val AWAIT_SECONDS = 20L

        val FEED_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>テスト番組</title>
                <item>
                  <title>第1回</title>
                  <guid>guid-1</guid>
                  <enclosure url="https://example.test/ep1.mp3" length="1000" />
                </item>
              </channel>
            </rss>
            """
                .trimIndent()
    }
}
