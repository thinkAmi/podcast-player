package dev.thinkami.podcastplayer.ui

import dev.thinkami.podcastplayer.logic.model.PlayedSnapshot

/**
 * 手動で視聴済みにした操作の、取り消し猶予つきの表現。
 *
 * 手動操作は誤タップの可能性があり、DLファイルの削除は取り消せない(古いエピソードは フィードから消えて再DLできないことがある)。そのため手動経路にだけ猶予を設ける。
 * 自動判定(残り10秒到達)には猶予を設けない — 誤発動の余地がないため。
 */
data class UndoablePlayedChange(
    val message: String,
    /** 操作前の状態。取り消し時にここへ戻す。 */
    val snapshots: List<PlayedSnapshot>,
    /** 猶予が過ぎたら削除判定にかけるエピソード。 */
    val affectedEpisodeIds: List<Long>,
) {
    companion object {
        /** 取り消しを受け付ける時間。 */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/**
 * 視聴済みにしたときに何が起きたのかを伝える文言。
 *
 * 一覧の行と統合エピソード画面で同じ操作が同じ意味を持つ以上、伝え方も揃える。 黙って再生を止めたりファイルを消したりしない。
 */
fun playedMessage(downloaded: Boolean, stopsPlayback: Boolean): String =
    when {
        stopsPlayback && downloaded -> "視聴済みにして再生を停止しました。ダウンロードを削除します"
        stopsPlayback -> "視聴済みにして再生を停止しました"
        downloaded -> "視聴済みにしました。ダウンロードを削除します"
        else -> "視聴済みにしました"
    }
