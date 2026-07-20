package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {

    @Test
    fun `次のDL済みエピソードへ続く`() {
        val list = listOf(episode(id = 1L, downloaded = true), episode(id = 2L, downloaded = true))
        assertEquals(2L, PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 1L)?.id)
    }

    @Test
    fun `未DLはスキップしてその先のDL済みを選ぶ`() {
        val list =
            listOf(
                episode(id = 1L, downloaded = true),
                episode(id = 2L, downloaded = false),
                episode(id = 3L, downloaded = true),
            )
        assertEquals(3L, PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 1L)?.id)
    }

    @Test
    fun `以降にDL済みがなければ停止する`() {
        val list = listOf(episode(id = 1L, downloaded = true), episode(id = 2L, downloaded = false))
        assertNull(PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 1L))
    }

    @Test
    fun `最後のエピソードなら停止する`() {
        val list = listOf(episode(id = 1L, downloaded = true))
        assertNull(PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 1L))
    }

    @Test
    fun `前方は探さない_視聴済みの巻き戻りを起こさない`() {
        val list = listOf(episode(id = 1L, downloaded = true), episode(id = 2L, downloaded = true))
        assertNull(PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 2L))
    }

    @Test
    fun `リストに現在のエピソードがなければ何も再生しない`() {
        val list = listOf(episode(id = 1L, downloaded = true))
        assertNull(PlaybackQueue.nextAutoPlayable(list, currentEpisodeId = 99L))
    }

    @Test
    fun `空のリストなら何も再生しない`() {
        assertNull(PlaybackQueue.nextAutoPlayable(emptyList(), currentEpisodeId = 1L))
    }
}
