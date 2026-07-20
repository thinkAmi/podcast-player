package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode

/**
 * 「聴き終わったか」「ファイルを消してよいか」の判断。
 *
 * ここは純粋関数だけを置く。実際にDBを更新したりファイルを削除したりするのは data 層の仕事で、 この分離がモックレス・Robolectricレスのテスト戦略の前提になっている。
 */
object ListeningRules {

    /**
     * 再生位置がこの秒数以内まで来たら「最後まで聴いた」とみなす。
     *
     * エンディングの提供クレジットで止めても視聴済みにしたいが、大きすぎると本編を聴き残した まま視聴済み=自動削除されてしまう。確実に最後まで聴く運用を前提に短めに取っている。
     * 設定画面は設けない。変えたくなったらこの定数を書き換えてビルドする。
     */
    const val COMPLETION_THRESHOLD_MS: Long = 10_000L

    /**
     * 再生完了(=自動で視聴済みにしてよい)かどうか。
     *
     * 長さが不明なフィードがあるため [durationMs] は null を許容し、その場合は自動判定しない (手動で視聴済みにできる)。
     */
    fun isPlaybackComplete(positionMs: Long, durationMs: Long?): Boolean {
        if (durationMs == null || durationMs <= 0L) return false
        return positionMs >= durationMs - COMPLETION_THRESHOLD_MS
    }

    /**
     * 視聴済みになったエピソードのDLファイルを削除してよいかどうか。
     *
     * favorite は MVP では常に false だが、判定は初日からここに書いておく。後日★ボタンを 足すときにこのロジックを変更しなくて済む。
     */
    fun shouldDeleteDownload(episode: Episode): Boolean =
        episode.played && episode.downloaded && !episode.favorite

    /** 再生位置を復元すべき位置。完了済みのものは先頭から聴き直せるようにする。 */
    fun resumePositionMs(episode: Episode): Long =
        if (episode.played) 0L else episode.positionMs.coerceAtLeast(0L)
}
