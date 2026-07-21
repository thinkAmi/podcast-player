package dev.thinkami.podcastplayer.data.rss

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.thinkami.podcastplayer.logic.rss.RssInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * XmlPullParser は Android の API のため、このテストは実機(計装テスト)でのみ動く。 値の解釈そのものは logic 層の JVM ユニットテストで網羅している。
 */
@RunWith(AndroidJUnit4::class)
class RssXmlReaderTest {

    private lateinit var xml: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        xml = context.assets.open("messy_feed.xml").use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @Test
    fun 番組のタイトルとアートワークを取り出す() {
        val parsed = RssXmlReader().read(xml)

        assertEquals("テスト番組", parsed.title)
        assertEquals("https://example.test/artwork.jpg", parsed.artworkUrl)
    }

    @Test
    fun すべてのitemを走査する_壊れたものも含めて取り出す() {
        val parsed = RssXmlReader().read(xml)

        // 走査の段階では捨てない。取り込むかどうかの判断は logic 層が行う。
        assertEquals(4, parsed.items.size)
    }

    @Test
    fun 音声のないitemは取り込まれない() {
        val parsed = RssXmlReader().read(xml)

        val normalized = RssInterpretation.normalizeAll(parsed.items)

        assertEquals(3, normalized.size)
        assertTrue(normalized.none { it.title.contains("壊れた") })
    }

    @Test
    fun CDATAのショーノートを取り出す() {
        val parsed = RssXmlReader().read(xml)

        val third = parsed.items.first { it.guid == "ep-003" }

        assertTrue(third.description!!.contains("CDATAに入ったショーノート"))
    }

    @Test
    fun contentEncodedをショーノートとして優先する() {
        val parsed = RssXmlReader().read(xml)

        val second = parsed.items.first { it.title!!.startsWith("第2回") }

        assertTrue(second.description!!.contains("content:encoded のショーノート"))
    }

    @Test
    fun guidのないitemは音声URLが一意キーになる() {
        val parsed = RssXmlReader().read(xml)

        val second = RssInterpretation.normalize(parsed.items.first { it.guid == null })!!

        assertEquals("https://example.test/ep002.mp3", second.guid)
    }

    @Test
    fun 長さの表記ゆれを解釈する() {
        val normalized =
            RssInterpretation.normalizeAll(RssXmlReader().read(xml).items).associateBy { it.guid }

        assertEquals(3_725_000L, normalized.getValue("ep-003").durationMs)
        assertEquals(3_130_000L, normalized.getValue("https://example.test/ep002.mp3").durationMs)
        assertEquals(1_800_000L, normalized.getValue("ep-001").durationMs)
    }

    @Test
    fun サイズのないenclosureはnullとして扱う() {
        val normalized =
            RssInterpretation.normalizeAll(RssXmlReader().read(xml).items).associateBy { it.guid }

        assertEquals(52_428_800L, normalized.getValue("ep-003").enclosureSizeBytes)
        assertNull(normalized.getValue("ep-001").enclosureSizeBytes)
    }

    @Test
    fun 日付が壊れていても他の項目は取り込む() {
        val normalized =
            RssInterpretation.normalizeAll(RssXmlReader().read(xml).items).associateBy { it.guid }

        val broken = normalized.getValue("ep-001")
        assertEquals(0L, broken.publishedAtEpochMillis)
        assertTrue(broken.title.startsWith("第1回"))
    }
}
