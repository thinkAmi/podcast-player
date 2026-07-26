package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningRulesTest {

    private val duration = 3_600_000L

    @Test
    fun `残り閾値ちょうどまで再生したら完了とみなす`() {
        val position = duration - ListeningRules.COMPLETION_THRESHOLD_MS
        assertTrue(ListeningRules.isPlaybackComplete(position, duration))
    }

    @Test
    fun `残りが閾値より多ければ完了とみなさない`() {
        val position = duration - ListeningRules.COMPLETION_THRESHOLD_MS - 1L
        assertFalse(ListeningRules.isPlaybackComplete(position, duration))
    }

    @Test
    fun `末尾まで再生したら完了とみなす`() {
        assertTrue(ListeningRules.isPlaybackComplete(duration, duration))
    }

    @Test
    fun `長さが不明なら自動判定しない`() {
        assertFalse(ListeningRules.isPlaybackComplete(duration, null))
    }

    @Test
    fun `長さが0以下なら自動判定しない`() {
        assertFalse(ListeningRules.isPlaybackComplete(0L, 0L))
        assertFalse(ListeningRules.isPlaybackComplete(0L, -1L))
    }

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
