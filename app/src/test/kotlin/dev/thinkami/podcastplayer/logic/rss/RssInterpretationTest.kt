package dev.thinkami.podcastplayer.logic.rss

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RssInterpretationTest {

    private fun item(
        guid: String? = "guid-1",
        title: String? = "エピソード",
        description: String? = "ショーノート",
        pubDate: String? = "Sun, 19 Jul 2026 09:00:00 +0900",
        enclosureUrl: String? = "https://example.test/ep.mp3",
        enclosureLength: String? = "52428800",
        itunesDuration: String? = "52:10",
    ) = ParsedItem(guid, title, description, pubDate, enclosureUrl, enclosureLength, itunesDuration)

    @Test
    fun `RFC 822 の日付を解釈する`() {
        val expected = ZonedDateTime.parse("2026-07-19T09:00:00+09:00").toInstant().toEpochMilli()
        assertEquals(
            expected,
            RssInterpretation.parsePublishedAt("Sun, 19 Jul 2026 09:00:00 +0900"),
        )
    }

    @Test
    fun `曜日のない日付も解釈する`() {
        val expected = ZonedDateTime.parse("2026-07-19T09:00:00+09:00").toInstant().toEpochMilli()
        assertEquals(expected, RssInterpretation.parsePublishedAt("19 Jul 2026 09:00:00 +0900"))
    }

    @Test
    fun `秒のない日付も解釈する`() {
        val expected = ZonedDateTime.parse("2026-07-19T09:00:00+09:00").toInstant().toEpochMilli()
        assertEquals(expected, RssInterpretation.parsePublishedAt("Sun, 19 Jul 2026 09:00 +0900"))
    }

    @Test
    fun `ISO 8601 の日付も解釈する`() {
        val expected = ZonedDateTime.parse("2026-07-19T09:00:00+09:00").toInstant().toEpochMilli()
        assertEquals(expected, RssInterpretation.parsePublishedAt("2026-07-19T09:00:00+09:00"))
    }

    @Test
    fun `解釈できない日付は null`() {
        assertNull(RssInterpretation.parsePublishedAt("いつか"))
        assertNull(RssInterpretation.parsePublishedAt(""))
        assertNull(RssInterpretation.parsePublishedAt(null))
    }

    @Test
    fun `長さは時分秒で解釈する`() {
        assertEquals(3_725_000L, RssInterpretation.parseDurationMs("01:02:05"))
    }

    @Test
    fun `長さは分秒でも解釈する`() {
        assertEquals(3_130_000L, RssInterpretation.parseDurationMs("52:10"))
    }

    @Test
    fun `長さは秒数だけでも解釈する`() {
        assertEquals(90_000L, RssInterpretation.parseDurationMs("90"))
    }

    @Test
    fun `解釈できない長さは null`() {
        assertNull(RssInterpretation.parseDurationMs("約1時間"))
        assertNull(RssInterpretation.parseDurationMs("1:2:3:4"))
        assertNull(RssInterpretation.parseDurationMs("0"))
        assertNull(RssInterpretation.parseDurationMs(null))
    }

    @Test
    fun `サイズは正の整数のみ受け付ける`() {
        assertEquals(1_024L, RssInterpretation.parseSizeBytes("1024"))
        assertNull(RssInterpretation.parseSizeBytes("0"))
        assertNull(RssInterpretation.parseSizeBytes("大きい"))
        assertNull(RssInterpretation.parseSizeBytes(null))
    }

    @Test
    fun `音声URLのないitemは取り込まない`() {
        assertNull(RssInterpretation.normalize(item(enclosureUrl = null)))
        assertNull(RssInterpretation.normalize(item(enclosureUrl = "  ")))
    }

    @Test
    fun `guidがなければ音声URLを一意キーに使う`() {
        val normalized = RssInterpretation.normalize(item(guid = null))
        assertEquals("https://example.test/ep.mp3", normalized?.guid)
    }

    @Test
    fun `タイトルがなければ代替表記を使う`() {
        assertEquals(
            RssInterpretation.UNTITLED,
            RssInterpretation.normalize(item(title = ""))?.title,
        )
    }

    @Test
    fun `日付が壊れていても取り込みは続行する`() {
        val normalized = RssInterpretation.normalize(item(pubDate = "壊れた日付"))
        assertNotNull(normalized)
        assertEquals(0L, normalized?.publishedAtEpochMillis)
    }

    @Test
    fun `壊れたitemだけを捨てて残りを取り込む`() {
        val items =
            listOf(
                item(guid = "ok-1"),
                item(guid = "broken", enclosureUrl = null),
                item(guid = "ok-2"),
            )

        val result = RssInterpretation.normalizeAll(items)

        assertEquals(listOf("ok-1", "ok-2"), result.map { it.guid })
    }

    @Test
    fun `ショーノートが空なら null にする`() {
        assertNull(RssInterpretation.normalize(item(description = "   "))?.showNotes)
    }
}
