package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.EpisodeFilter

/**
 * 絞り込み条件の適用。
 *
 * 実際の一覧表示はRoomのWHERE句で絞る(DBを書けば画面が追随する構造にするため)。ここの実装は 同じ条件を宣言的に表現したもので、再生の自動継続など「いま表示されているリスト」を前提に
 * 判断する箇所と、条件そのもののテストに使う。両者は同じ意味でなければならない。
 */
object EpisodeFiltering {

    /** エピソード1件が条件を満たすか。2条件のAND。 */
    fun matches(episode: Episode, filter: EpisodeFilter): Boolean {
        val satisfiesUnplayed = !filter.unplayedOnly || !episode.played
        val satisfiesDownloaded = !filter.downloadedOnly || episode.downloaded
        return satisfiesUnplayed && satisfiesDownloaded
    }

    /** リストに条件を適用する。並び順は呼び出し側が決めた順序を保つ。 */
    fun apply(episodes: List<Episode>, filter: EpisodeFilter): List<Episode> = episodes.filter {
        matches(it, filter)
    }
}
