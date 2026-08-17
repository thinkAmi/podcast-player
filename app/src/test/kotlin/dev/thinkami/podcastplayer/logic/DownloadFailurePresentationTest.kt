package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailurePresentationTest {

    /**
     * 種別ごとの文言と再試行案内の有無。
     *
     * [DownloadFailure.HttpStatus] だけはコードの分だけ値があるので代表値で確かめ、 残りは object なので全数を並べてある。
     */
    private val expectations: Map<DownloadFailure, Expected> =
        mapOf(
            DownloadFailure.UnsupportedUrl to Expected("DL失敗(取得できないURL)", suggestsRetry = false),
            DownloadFailure.HttpStatus(404) to Expected("DL失敗(HTTP 404)", suggestsRetry = true),
            DownloadFailure.HttpStatus(500) to Expected("DL失敗(HTTP 500)", suggestsRetry = true),
            DownloadFailure.HttpStatus(403) to Expected("DL失敗(HTTP 403)", suggestsRetry = true),
            DownloadFailure.Connection to Expected("DL失敗(接続できず)", suggestsRetry = true),
            DownloadFailure.CompressedResponse to Expected("DL失敗(非対応の応答)", suggestsRetry = true),
            DownloadFailure.Save to Expected("DL失敗(保存できず)", suggestsRetry = false),
        )

    @Test
    fun `種別ごとに決まった文言と再試行案内を返す`() {
        expectations.forEach { (failure, expected) ->
            assertEquals(
                failure.toString(),
                expected.label,
                DownloadFailurePresentation.label(failure),
            )
            assertEquals(
                failure.toString(),
                expected.suggestsRetry,
                DownloadFailurePresentation.suggestsRetry(failure),
            )
        }
    }

    @Test
    fun `HTTPステータスはコードをそのまま出す`() {
        assertEquals(
            "DL失敗(HTTP 418)",
            DownloadFailurePresentation.label(DownloadFailure.HttpStatus(418)),
        )
    }

    @Test
    fun `やり直しても変わらない種別だけ再試行を勧めない`() {
        assertFalse(DownloadFailurePresentation.suggestsRetry(DownloadFailure.UnsupportedUrl))
        assertFalse(DownloadFailurePresentation.suggestsRetry(DownloadFailure.Save))
        assertTrue(DownloadFailurePresentation.suggestsRetry(DownloadFailure.Connection))
    }

    /** 行に出す文言は2行目に収まる長さに保つ(長い理由は detail に持たせて行には出さない)。 */
    @Test
    fun `文言は短く保つ`() {
        expectations.values.forEach { expected ->
            assertTrue(expected.label, expected.label.length <= MAX_LABEL_LENGTH)
        }
    }

    private data class Expected(val label: String, val suggestsRetry: Boolean)

    private companion object {
        const val MAX_LABEL_LENGTH = 20
    }
}
