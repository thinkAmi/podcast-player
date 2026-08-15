package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPresentationTest {

    // ---- sampleSizeFor ----

    @Test
    fun `目標より小さい元画像は縮小しない`() {
        assertEquals(1, ArtworkPresentation.sampleSizeFor(100, 100, 200))
    }

    @Test
    fun `目標と同じ大きさなら縮小しない`() {
        assertEquals(1, ArtworkPresentation.sampleSizeFor(200, 200, 200))
    }

    @Test
    fun `半分にしても目標を保てるなら2で縮める`() {
        assertEquals(2, ArtworkPresentation.sampleSizeFor(400, 400, 200))
    }

    @Test
    fun `目標を割る手前で止める`() {
        // 3000/8=375 は目標以上だが 3000/16=187 は目標を割るため 8 を選ぶ。
        assertEquals(8, ArtworkPresentation.sampleSizeFor(3000, 3000, 200))
    }

    @Test
    fun `非正方形は短辺を基準にする`() {
        // 長辺基準だと 4 を返して短辺が 100px まで落ちる。短辺基準なら 2 で止まる。
        assertEquals(2, ArtworkPresentation.sampleSizeFor(800, 400, 200))
    }

    @Test
    fun `寸法が読めなかった場合は縮小しない`() {
        // BitmapFactory は寸法取得に失敗すると -1 を返す。
        assertEquals(1, ArtworkPresentation.sampleSizeFor(-1, -1, 200))
    }

    @Test
    fun `幅だけ不正でも縮小しない`() {
        assertEquals(1, ArtworkPresentation.sampleSizeFor(0, 400, 200))
    }

    @Test
    fun `目標が0以下なら縮小しない`() {
        assertEquals(1, ArtworkPresentation.sampleSizeFor(400, 400, 0))
        assertEquals(1, ArtworkPresentation.sampleSizeFor(400, 400, -10))
    }

    @Test
    fun `返す値は常に2の冪`() {
        val sizes = listOf(1, 37, 100, 512, 999, 3000, 10_000)
        sizes.forEach { source ->
            sizes.forEach { target ->
                val sampleSize = ArtworkPresentation.sampleSizeFor(source, source, target)
                assertTrue(
                    "source=$source target=$target sampleSize=$sampleSize",
                    sampleSize > 0 && sampleSize and (sampleSize - 1) == 0,
                )
            }
        }
    }

    @Test
    fun `縮小しても目標サイズを下回らない`() {
        val sizes = listOf(1, 37, 100, 512, 999, 3000, 10_000)
        sizes.forEach { source ->
            sizes.forEach { target ->
                val decoded = source / ArtworkPresentation.sampleSizeFor(source, source, target)
                // 元画像が目標より小さい場合を除き、デコード後も目標を保つ。
                assertTrue(
                    "source=$source target=$target decoded=$decoded",
                    source < target || decoded >= target,
                )
            }
        }
    }

    // ---- monogramFor ----

    @Test
    fun `先頭の1文字を大文字で返す`() {
        assertEquals("R", ArtworkPresentation.monogramFor("rebuild"))
    }

    @Test
    fun `日本語のタイトルは先頭の1文字をそのまま返す`() {
        assertEquals("ゆ", ArtworkPresentation.monogramFor("ゆる言語学ラジオ"))
    }

    @Test
    fun `先頭の空白は読み飛ばす`() {
        assertEquals("A", ArtworkPresentation.monogramFor("   after hours"))
    }

    @Test
    fun `絵文字始まりでもサロゲートペアを割らない`() {
        val monogram = ArtworkPresentation.monogramFor("🎧 ヘッドホン番組")
        assertEquals("🎧", monogram)
    }

    @Test
    fun `空文字は代替文字を返す`() {
        assertEquals(ArtworkPresentation.FALLBACK_MONOGRAM, ArtworkPresentation.monogramFor(""))
    }

    @Test
    fun `空白のみのタイトルは代替文字を返す`() {
        assertEquals(
            ArtworkPresentation.FALLBACK_MONOGRAM,
            ArtworkPresentation.monogramFor("   \t  "),
        )
    }

    @Test
    fun `どんな文字列でも例外を投げず1文字ぶんを返す`() {
        val titles = listOf("", " ", "\n", "a", "あ", "🎧", "👨‍👩‍👧", "\uD83C", "ＡＢＣ", "123")
        titles.forEach { title ->
            val monogram = ArtworkPresentation.monogramFor(title)
            assertTrue("title=$title monogram=$monogram", monogram.isNotEmpty())
        }
    }
}
