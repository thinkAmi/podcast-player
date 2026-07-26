package dev.thinkami.podcastplayer.logic.rss

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * parseDurationMs の性質検証。例ベースでは列挙しきれない入力空間(任意の文字列・任意の数値成分) に対する不変条件を守る。失敗時は kotest
 * が反例を最小化しシードを保存するため、そのまま回帰 テストとして機能する。
 */
class RssInterpretationPropertyTest {

    @Test
    fun `正常な時分秒は合計秒のミリ秒へ往復する`() {
        runBlocking {
            checkAll(Arb.int(0..99), Arb.int(0..59), Arb.int(0..59)) { h, m, s ->
                val totalSeconds = h * 3_600L + m * 60L + s
                val expected = if (totalSeconds > 0L) totalSeconds * 1_000L else null
                assertEquals(expected, RssInterpretation.parseDurationMs("$h:$m:$s"))
            }
        }
    }

    @Test
    fun `分秒の2成分表記も往復する`() {
        runBlocking {
            checkAll(Arb.int(0..999), Arb.int(0..59)) { m, s ->
                val totalSeconds = m * 60L + s
                val expected = if (totalSeconds > 0L) totalSeconds * 1_000L else null
                assertEquals(expected, RssInterpretation.parseDurationMs("$m:$s"))
            }
        }
    }

    @Test
    fun `負の成分を含む時分秒は解釈できない`() {
        runBlocking {
            checkAll(Arb.int(-99..99), Arb.int(-59..59), Arb.int(-59..-1)) { h, m, s ->
                assertNull(RssInterpretation.parseDurationMs("$h:$m:$s"))
                assertNull(RssInterpretation.parseDurationMs("$m:$s"))
            }
        }
    }

    @Test
    fun `秒数だけの表記は範囲内なら往復し範囲外なら解釈できない`() {
        runBlocking {
            checkAll(Arb.long(1L..Long.MAX_VALUE)) { seconds ->
                val result = RssInterpretation.parseDurationMs(seconds.toString())
                if (seconds <= Long.MAX_VALUE / 1_000L) {
                    assertEquals(seconds * 1_000L, result)
                } else {
                    assertNull(result)
                }
            }
        }
    }

    @Test
    fun `任意の文字列に対して例外を投げず null か正の値だけを返す`() {
        runBlocking {
            checkAll(Arb.string(0..40)) { raw ->
                val result = RssInterpretation.parseDurationMs(raw)
                assertTrue(result == null || result > 0L)
            }
        }
    }
}
