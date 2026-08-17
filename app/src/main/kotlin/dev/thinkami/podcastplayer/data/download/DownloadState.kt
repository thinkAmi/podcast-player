package dev.thinkami.podcastplayer.data.download

import dev.thinkami.podcastplayer.logic.DownloadFailure

/** エピソード1件のダウンロードの進み具合。UIの行表示に使う。 */
sealed interface DownloadState {

    /** 実行中でない。DL済みかどうかはDBの downloaded を見る。 */
    data object Idle : DownloadState

    data class InProgress(val bytesRead: Long, val totalBytes: Long) : DownloadState {
        /** 全体サイズが不明なフィードもあるため、割合は算出できないことがある。 */
        val fraction: Float?
            get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes) else null
    }

    /**
     * 失敗。自動では再試行しない(通信は利用者の操作を起点にする原則)。 行に失敗表示を出し、再タップで最初からやり直す。
     *
     * [detail] は URL 全文や例外メッセージのような長い情報。行には出さず、種別([failure])だけを 表示に使う。
     */
    data class Failed(val failure: DownloadFailure, val detail: String? = null) : DownloadState
}
