package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * nextAutoPlayable と playbackOrderFrom は「フィルター適用後のリスト順=再生順」という同じ解釈の
 * 2通りの実装。乖離すると「タップして始めた順」と「自動継続の順」がずれるため、任意のDL状態の 組み合わせで両者が一致することを検証する。
 */
class PlaybackQueuePropertyTest {

    private fun episodes(downloadedFlags: List<Boolean>): List<Episode> =
        downloadedFlags.mapIndexed { index, dl ->
            episode(id = index + 1L, downloaded = dl)
        }

    @Test
    fun `再生順はDL済みの選択エピソードから始まり以降もDL済みだけが元の順序で並ぶ`() {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 1..20), Arb.int(0..99)) { flags, pick ->
                val list = episodes(flags)
                val startIndex = pick % list.size
                val start = list[startIndex]
                val order = PlaybackQueue.playbackOrderFrom(list, start.id)
                if (start.downloaded) {
                    assertEquals(start.id, order.first().id)
                    assertTrue(order.all { it.downloaded })
                    // 選択位置以降のDL済み全件が、元リストの順序のまま過不足なく含まれる
                    val expectedIds = list.drop(startIndex).filter { it.downloaded }.map { it.id }
                    assertEquals(expectedIds, order.map { it.id })
                } else {
                    assertTrue(order.isEmpty())
                }
            }
        }
    }

    @Test
    fun `タップ再生の2曲目と自動継続の次曲は一致する`() {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 1..20), Arb.int(0..99)) { flags, pick ->
                val list = episodes(flags)
                val start = list[pick % list.size]
                val next = PlaybackQueue.nextAutoPlayable(list, start.id)
                if (start.downloaded) {
                    val order = PlaybackQueue.playbackOrderFrom(list, start.id)
                    assertEquals(next?.id, order.getOrNull(1)?.id)
                }
            }
        }
    }

    @Test
    fun `自動継続の次曲は現在曲より後の最初のDL済みである`() {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 1..20), Arb.int(0..99)) { flags, pick ->
                val list = episodes(flags)
                val currentIndex = pick % list.size
                val current = list[currentIndex]
                val next = PlaybackQueue.nextAutoPlayable(list, current.id)
                val after = list.drop(currentIndex + 1)
                if (next == null) {
                    assertTrue(after.none { it.downloaded })
                } else {
                    assertTrue(next.downloaded)
                    val nextPos = after.indexOfFirst { it.id == next.id }
                    assertTrue(nextPos >= 0)
                    assertTrue(after.take(nextPos).none { it.downloaded })
                }
            }
        }
    }

    @Test
    fun `リストに存在しないエピソードからは何も始まらない`() {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 0..20)) { flags ->
                val list = episodes(flags)
                val unknownId = list.size + 100L
                assertTrue(PlaybackQueue.playbackOrderFrom(list, unknownId).isEmpty())
                assertNull(PlaybackQueue.nextAutoPlayable(list, unknownId))
            }
        }
    }
}
