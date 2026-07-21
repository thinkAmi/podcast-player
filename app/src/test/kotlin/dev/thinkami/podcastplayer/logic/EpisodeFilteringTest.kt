package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.EpisodeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeFilteringTest {

    private val unplayedUndownloaded = episode(id = 1L)
    private val unplayedDownloaded = episode(id = 2L, downloaded = true)
    private val playedDownloaded = episode(id = 3L, played = true, downloaded = true)
    private val playedUndownloaded = episode(id = 4L, played = true)

    private val all =
        listOf(unplayedUndownloaded, unplayedDownloaded, playedDownloaded, playedUndownloaded)

    @Test
    fun `既定では絞り込まない`() {
        assertEquals(all, EpisodeFiltering.apply(all, EpisodeFilter.NONE))
    }

    @Test
    fun `未聴のみ`() {
        val filtered = EpisodeFiltering.apply(all, EpisodeFilter(unplayedOnly = true))
        assertEquals(listOf(unplayedUndownloaded, unplayedDownloaded), filtered)
    }

    @Test
    fun `DL済みのみ`() {
        val filtered = EpisodeFiltering.apply(all, EpisodeFilter(downloadedOnly = true))
        assertEquals(listOf(unplayedDownloaded, playedDownloaded), filtered)
    }

    @Test
    fun `未聴かつDL済み_いま聴けるものだけが残る`() {
        val filter = EpisodeFilter(unplayedOnly = true, downloadedOnly = true)
        assertEquals(listOf(unplayedDownloaded), EpisodeFiltering.apply(all, filter))
    }

    @Test
    fun `絞り込んでも元の並び順を保つ`() {
        val reversed = all.reversed()
        val filtered = EpisodeFiltering.apply(reversed, EpisodeFilter(downloadedOnly = true))
        assertEquals(listOf(playedDownloaded, unplayedDownloaded), filtered)
    }

    @Test
    fun `個別判定_未聴のみの条件で視聴済みは弾かれる`() {
        val filter = EpisodeFilter(unplayedOnly = true)
        assertFalse(EpisodeFiltering.matches(playedDownloaded, filter))
        assertTrue(EpisodeFiltering.matches(unplayedDownloaded, filter))
    }

    @Test
    fun `個別判定_DL済みのみの条件で未DLは弾かれる`() {
        val filter = EpisodeFilter(downloadedOnly = true)
        assertFalse(EpisodeFiltering.matches(unplayedUndownloaded, filter))
        assertTrue(EpisodeFiltering.matches(unplayedDownloaded, filter))
    }
}
