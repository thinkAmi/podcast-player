package dev.thinkami.podcastplayer.ui

import dev.thinkami.podcastplayer.data.EpisodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 取り消し猶予つきの視聴済み操作を、画面をまたいで1つだけ持つ。
 *
 * 猶予の持ち主を画面(ViewModel)にすると、操作した画面が閉じたときに猶予ごと消えるか、 遷移先で取り消せなくなる。視聴済みにしてから戻る経路(統合エピソード画面)があるため、
 * 猶予はアプリ生存期間のここに置き、どの画面からでも同じ1件を見て確定・取り消しできるようにする。
 *
 * 猶予の確定(=ファイル削除)は画面の生死に左右されてはならないので、実行もここが引き受ける。
 */
class PlayedUndoHolder(private val episodeRepository: EpisodeRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutablePending = MutableStateFlow<UndoablePlayedChange?>(null)
    val pending: StateFlow<UndoablePlayedChange?> = mutablePending.asStateFlow()

    fun record(change: UndoablePlayedChange) {
        mutablePending.value = change
    }

    /** 猶予が過ぎた。ここで初めてファイルを消す。 */
    fun commit() {
        val change = mutablePending.value ?: return
        mutablePending.value = null
        scope.launch { episodeRepository.deleteDownloadsIfEligible(change.affectedEpisodeIds) }
    }

    /** 取り消し。視聴状態だけを操作前に戻す(停止した再生は再開しない)。 */
    fun undo() {
        val change = mutablePending.value ?: return
        mutablePending.value = null
        scope.launch { episodeRepository.restorePlayed(change.snapshots) }
    }
}
