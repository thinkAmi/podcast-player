package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** リストは一覧と同じ新しい順で渡す。id が大きいほど新しい回として並べている。 */
class PlaybackQueueTest {

    @Test
    fun `選んだ回から新しい方へ順に続く`() {
        val list =
            listOf(
                episode(id = 4L, downloaded = true),
                episode(id = 3L, downloaded = true),
                episode(id = 2L, downloaded = true),
                episode(id = 1L, downloaded = true),
            )

        val order = PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 2L)

        assertEquals(listOf(2L, 3L, 4L), order.map { it.id })
    }

    @Test
    fun `未DLはスキップして新しい方のDL済みだけが続く`() {
        val list =
            listOf(
                episode(id = 4L, downloaded = true),
                episode(id = 3L, downloaded = false),
                episode(id = 2L, downloaded = true),
                episode(id = 1L, downloaded = true),
            )

        val order = PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 2L)

        assertEquals(listOf(2L, 4L), order.map { it.id })
    }

    @Test
    fun `選んだ回より古い回は含めない`() {
        val list =
            listOf(
                episode(id = 3L, downloaded = true),
                episode(id = 2L, downloaded = true),
                episode(id = 1L, downloaded = true),
            )

        val order = PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 2L)

        assertEquals(listOf(2L, 3L), order.map { it.id })
    }

    @Test
    fun `最新の回を選んだらそれ1件で終わる`() {
        val list =
            listOf(
                episode(id = 3L, downloaded = true),
                episode(id = 2L, downloaded = true),
                episode(id = 1L, downloaded = true),
            )

        val order = PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 3L)

        assertEquals(listOf(3L), order.map { it.id })
    }

    @Test
    fun `新しい方にDL済みがなければ選んだ回だけで止まる`() {
        val list =
            listOf(
                episode(id = 3L, downloaded = false),
                episode(id = 2L, downloaded = true),
                episode(id = 1L, downloaded = true),
            )

        val order = PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 2L)

        assertEquals(listOf(2L), order.map { it.id })
    }

    @Test
    fun `未DLのエピソードからは再生を始められない`() {
        val list = listOf(episode(id = 2L, downloaded = true), episode(id = 1L, downloaded = false))

        assertEquals(emptyList<Long>(), PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 1L))
    }

    @Test
    fun `リストにないエピソードからは再生を始められない`() {
        val list = listOf(episode(id = 1L, downloaded = true))

        assertEquals(emptyList<Long>(), PlaybackQueue.playbackOrderFrom(list, startEpisodeId = 99L))
    }

    @Test
    fun `空のリストからは再生を始められない`() {
        assertEquals(
            emptyList<Long>(),
            PlaybackQueue.playbackOrderFrom(emptyList(), startEpisodeId = 1L),
        )
    }
}
