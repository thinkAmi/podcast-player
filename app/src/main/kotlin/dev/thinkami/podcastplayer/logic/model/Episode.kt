package dev.thinkami.podcastplayer.logic.model

/**
 * エピソード1件。Android APIにもRoomにも依存しない純粋なドメインモデル。
 *
 * 状態は「未聴/視聴済み」「未DL/DL済み」「再生位置」の3軸で表され、アプリ全体はこの状態機械を 中心に回る。[favorite]
 * はMVPではUIを持たないが、視聴済み時の自動削除から除外する判定に 初日から使う(後日★ボタンを足すときに削除ロジックを変更しなくて済むようにするため)。
 */
data class Episode(
    val id: Long,
    val feedId: Long,
    /** フィードが与える一意ID。既知/新規の判定に使う。 */
    val guid: String,
    val title: String,
    /** ショーノート。フィードのdescription。 */
    val showNotes: String?,
    val publishedAtEpochMillis: Long,
    /** フィードが長さを申告しない場合があるため null を許容する。 */
    val durationMs: Long?,
    /** 音声ファイルのURL。削除後も再DLできるよう保持し続ける。 */
    val enclosureUrl: String,
    /** モバイル回線時の確認ダイアログでサイズを表示するために使う。 */
    val enclosureSizeBytes: Long?,
    val played: Boolean,
    val downloaded: Boolean,
    val localPath: String?,
    val positionMs: Long,
    val favorite: Boolean,
)
