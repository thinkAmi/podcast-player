package dev.thinkami.podcastplayer.logic.model

/**
 * 番組ごとのエピソード絞り込み条件。
 *
 * このアプリを自作した動機そのもの。2つの条件はANDで結合され、両方ONのとき 「未聴 かつ DL済み」= いま聴けるものだけが並ぶ。条件を増やさないこと。
 */
data class EpisodeFilter(val unplayedOnly: Boolean = false, val downloadedOnly: Boolean = false) {
    companion object {
        /** 新規購読時の既定値。購読直後は全件見たいので絞り込まない。 */
        val NONE = EpisodeFilter()
    }
}
