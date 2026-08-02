package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode

/**
 * 視聴状態にまつわる「ファイルを消してよいか」「どこから鳴らすか」「未聴が何件か」の判断。
 *
 * 「聴き終わったか」はここにはない。完了はプレイヤーが実際に鳴り終えたイベントで確定するため、 位置から計算する余地がない(player 層の PlaybackService が持つ)。
 *
 * ここは純粋関数だけを置く。実際にDBを更新したりファイルを削除したりするのは data 層の仕事で、 この分離がモックレス・Robolectricレスのテスト戦略の前提になっている。
 */
object ListeningRules {

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

    /**
     * 未聴エピソード数。購読一覧のバッジに表示する値の定義。
     *
     * 実際の一覧表示はRoomのCOUNTで数える(DBを書けば画面が追随する構造にするため)。ここの実装は
     * 同じ条件を宣言的に表現したもので、等価テストの参照実装として使う。両者は同じ意味でなければ ならない。
     */
    fun countUnplayed(episodes: List<Episode>): Int = episodes.count { !it.played }
}
