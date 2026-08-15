package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 再生順は「一覧(新しい順)を逆に辿る」という一つの規則で決まる。DL状態の並びは任意なので、 個別の例では踏み尽くせない組み合わせをここで確かめる。
 *
 * 検証するのは4つ: 先頭は必ず選んだ回であること、並ぶのはDL済みだけであること、順序が古い順 (=一覧の逆順)であること、選んだ回が鳴らせないなら何も始まらないこと。
 */
class PlaybackQueuePropertyTest {

    /** 一覧と同じ新しい順に並べる。id が大きいほど新しい回。 */
    private fun episodes(downloadedFlags: List<Boolean>): List<Episode> =
        downloadedFlags.mapIndexed { index, dl ->
            episode(id = (downloadedFlags.size - index).toLong(), downloaded = dl)
        }

    /** 任意のDL状態の一覧と、その中の任意の開始位置で検証する。 */
    private fun forEachStart(check: (List<Episode>, Int) -> Unit) {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 1..20), Arb.int(0..99)) { flags, pick ->
                val list = episodes(flags)
                check(list, pick % list.size)
            }
        }
    }

    @Test
    fun `先頭は選んだ回でありDL済みだけが並ぶ`() = forEachStart { list, startIndex ->
        val start = list[startIndex]
        val order = PlaybackQueue.playbackOrderFrom(list, start.id)
        if (!start.downloaded) return@forEachStart

        assertEquals(start.id, order.first().id)
        assertTrue(order.all { it.downloaded })
    }

    @Test
    fun `並びは一覧の逆順つまり古い順になる`() = forEachStart { list, startIndex ->
        val start = list[startIndex]
        val order = PlaybackQueue.playbackOrderFrom(list, start.id)
        if (!start.downloaded) return@forEachStart

        // 選ばれた顔ぶれをそのままに、一覧を逆から辿った順序と一致する(重複も混入もない)
        val selectedIds = order.map { it.id }.toSet()
        val oldestFirst = list.reversed().map { it.id }.filter { it in selectedIds }
        assertEquals(oldestFirst, order.map { it.id })
    }

    @Test
    fun `選んだ回より古い回は入らず新しい側のDL済みは漏れない`() = forEachStart { list, startIndex ->
        val start = list[startIndex]
        val order = PlaybackQueue.playbackOrderFrom(list, start.id)
        if (!start.downloaded) return@forEachStart

        val newerSide = list.take(startIndex + 1)
        assertTrue(order.all { queued -> newerSide.any { it.id == queued.id } })
        assertEquals(newerSide.count { it.downloaded }, order.size)
    }

    @Test
    fun `未DLの回からは何も始まらない`() = forEachStart { list, startIndex ->
        val start = list[startIndex]
        if (start.downloaded) return@forEachStart

        assertTrue(PlaybackQueue.playbackOrderFrom(list, start.id).isEmpty())
    }

    @Test
    fun `リストに存在しないエピソードからは何も始まらない`() {
        runBlocking {
            checkAll(Arb.list(Arb.boolean(), 0..20)) { flags ->
                val list = episodes(flags)
                val unknownId = list.size + 100L
                assertTrue(PlaybackQueue.playbackOrderFrom(list, unknownId).isEmpty())
            }
        }
    }
}
