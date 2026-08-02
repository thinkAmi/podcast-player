package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningRulesTest {

    @Test
    fun `視聴済みでDL済みなら削除対象`() {
        assertTrue(ListeningRules.shouldDeleteDownload(episode(played = true, downloaded = true)))
    }

    @Test
    fun `favoriteは視聴済みでも削除しない`() {
        val target = episode(played = true, downloaded = true, favorite = true)
        assertFalse(ListeningRules.shouldDeleteDownload(target))
    }

    @Test
    fun `未聴なら削除しない`() {
        assertFalse(ListeningRules.shouldDeleteDownload(episode(played = false, downloaded = true)))
    }

    @Test
    fun `未DLなら削除するファイルがない`() {
        assertFalse(ListeningRules.shouldDeleteDownload(episode(played = true, downloaded = false)))
    }

    @Test
    fun `未聴なら保存位置から再開する`() {
        assertEquals(1_234L, ListeningRules.resumePositionMs(episode(positionMs = 1_234L)))
    }

    @Test
    fun `視聴済みなら先頭から聴き直せる`() {
        val target = episode(played = true, positionMs = 1_234L)
        assertEquals(0L, ListeningRules.resumePositionMs(target))
    }

    @Test
    fun `負の再生位置は0に丸める`() {
        assertEquals(0L, ListeningRules.resumePositionMs(episode(positionMs = -5L)))
    }

    @Test
    fun `空リストの未聴数は0`() {
        assertEquals(0, ListeningRules.countUnplayed(emptyList()))
    }

    @Test
    fun `全件未聴なら件数がそのまま未聴数`() {
        val episodes = listOf(episode(id = 1L), episode(id = 2L), episode(id = 3L))
        assertEquals(3, ListeningRules.countUnplayed(episodes))
    }

    @Test
    fun `全件視聴済みなら未聴数は0`() {
        val episodes = listOf(episode(id = 1L, played = true), episode(id = 2L, played = true))
        assertEquals(0, ListeningRules.countUnplayed(episodes))
    }

    @Test
    fun `混在していれば未聴だけを数える`() {
        val episodes =
            listOf(
                episode(id = 1L, played = true),
                episode(id = 2L, played = false),
                episode(id = 3L, played = false),
            )
        assertEquals(2, ListeningRules.countUnplayed(episodes))
    }
}
