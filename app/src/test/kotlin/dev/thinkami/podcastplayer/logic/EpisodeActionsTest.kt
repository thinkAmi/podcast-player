package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeActionsTest {

    /** 4つの真偽値の全組み合わせ(16通り)の期待値。優先順位の意図をそのまま表にしてある。 */
    private val expectations: Map<Inputs, EpisodeAction> = buildMap {
        listOf(true, false).forEach { isCurrent ->
            listOf(true, false).forEach { isDownloaded ->
                listOf(true, false).forEach { hasFailed ->
                    // 実行中は他の何よりも優先する。
                    put(Inputs(true, isCurrent, isDownloaded, hasFailed), EpisodeAction.DOWNLOADING)
                }
            }
        }
        // 実行中でない場合。現在 > DL済み > 失敗 > 未DL の順で決まる。
        listOf(true, false).forEach { isDownloaded ->
            listOf(true, false).forEach { hasFailed ->
                put(
                    Inputs(false, true, isDownloaded, hasFailed),
                    EpisodeAction.TOGGLE_PLAY_PAUSE,
                )
            }
        }
        listOf(true, false).forEach { hasFailed ->
            put(Inputs(false, false, true, hasFailed), EpisodeAction.PLAY)
        }
        put(Inputs(false, false, false, true), EpisodeAction.RETRY_DOWNLOAD)
        put(Inputs(false, false, false, false), EpisodeAction.DOWNLOAD)
    }

    @Test
    fun `4つの真偽値の全組み合わせで期待どおりのアクションを返す`() {
        assertEquals("全16通りを網羅していること", 16, expectations.size)
        expectations.forEach { (inputs, expected) ->
            assertEquals(
                inputs.toString(),
                expected,
                EpisodeActions.actionFor(
                    isDownloading = inputs.isDownloading,
                    isCurrent = inputs.isCurrent,
                    isDownloaded = inputs.isDownloaded,
                    hasFailedDownload = inputs.hasFailedDownload,
                ),
            )
        }
    }

    @Test
    fun `現在のエピソードはDL済みでも開始ではなくトグル`() {
        val action =
            EpisodeActions.actionFor(
                isDownloading = false,
                isCurrent = true,
                isDownloaded = true,
                hasFailedDownload = false,
            )
        assertEquals(EpisodeAction.TOGGLE_PLAY_PAUSE, action)
    }

    @Test
    fun `DL済みなら過去の失敗表示は残さない`() {
        val action =
            EpisodeActions.actionFor(
                isDownloading = false,
                isCurrent = false,
                isDownloaded = true,
                hasFailedDownload = true,
            )
        assertEquals(EpisodeAction.PLAY, action)
    }

    private data class Inputs(
        val isDownloading: Boolean,
        val isCurrent: Boolean,
        val isDownloaded: Boolean,
        val hasFailedDownload: Boolean,
    )
}
