package dev.thinkami.podcastplayer.logic.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSourceValidationTest {

    @Test
    fun `全itemが取り込み先と同じ出典を申告していれば許可する`() {
        val verdict =
            ImportSourceValidation.validate(listOf(FEED_URL, FEED_URL, FEED_URL), FEED_URL)

        assertEquals(ImportSourceVerdict.Allowed, verdict)
    }

    @Test
    fun `出典の前後の空白は無視して照合する`() {
        val verdict =
            ImportSourceValidation.validate(listOf("  $FEED_URL  ", "\n$FEED_URL"), " $FEED_URL ")

        assertEquals(ImportSourceVerdict.Allowed, verdict)
    }

    @Test
    fun `出典のないitemが1件でもあれば拒否する`() {
        val verdict = ImportSourceValidation.validate(listOf(FEED_URL, null, FEED_URL), FEED_URL)

        assertEquals(ImportSourceVerdict.Undeclared, verdict)
    }

    @Test
    fun `出典が空白だけのitemは申告なしとして拒否する`() {
        val verdict = ImportSourceValidation.validate(listOf(FEED_URL, "   "), FEED_URL)

        assertEquals(ImportSourceVerdict.Undeclared, verdict)
    }

    @Test
    fun `どのitemも出典を申告していなければ拒否する`() {
        // 公式フィードそのものを取り込もうとした場合がこれにあたる
        val verdict = ImportSourceValidation.validate(listOf(null, null), FEED_URL)

        assertEquals(ImportSourceVerdict.Undeclared, verdict)
    }

    @Test
    fun `itemが1件もないXMLは拒否する`() {
        // 空集合を「全件一致」と判定すると、無関係な空のRSSが素通りしてしまう
        val verdict = ImportSourceValidation.validate(emptyList(), FEED_URL)

        assertEquals(ImportSourceVerdict.Undeclared, verdict)
    }

    @Test
    fun `itemごとに出典が違えば拒否する`() {
        val verdict =
            ImportSourceValidation.validate(listOf(FEED_URL, OTHER_FEED_URL, FEED_URL), FEED_URL)

        assertEquals(ImportSourceVerdict.Mixed(listOf(FEED_URL, OTHER_FEED_URL)), verdict)
    }

    @Test
    fun `申告された出典が取り込み先と違えば拒否する`() {
        // 引数の取り違え(別番組の購読へ注入しようとした)がこれにあたる
        val verdict = ImportSourceValidation.validate(listOf(OTHER_FEED_URL), FEED_URL)

        assertEquals(
            ImportSourceVerdict.Mismatched(declared = OTHER_FEED_URL, subscribed = FEED_URL),
            verdict,
        )
    }

    @Test
    fun `スキームだけが違う出典は不一致として扱う`() {
        // 購読登録時の文字列と一字一句同じであることを要求する(表記ゆれの吸収はしない)
        val httpUrl = FEED_URL.replace("https://", "http://")

        val verdict = ImportSourceValidation.validate(listOf(httpUrl), FEED_URL)

        assertEquals(
            ImportSourceVerdict.Mismatched(declared = httpUrl, subscribed = FEED_URL),
            verdict,
        )
    }

    @Test
    fun `混在は不一致より先に判定する`() {
        // 混在しているときは「どれと照合すべきか」が決まらないため、混在として報告する
        val verdict =
            ImportSourceValidation.validate(
                listOf(OTHER_FEED_URL, "https://third.test/rss"),
                FEED_URL,
            )

        assertEquals(
            ImportSourceVerdict.Mixed(listOf(OTHER_FEED_URL, "https://third.test/rss")),
            verdict,
        )
    }

    private companion object {
        const val FEED_URL = "https://example.test/feed.xml"
        const val OTHER_FEED_URL = "https://other.test/podcast/rss"
    }
}
