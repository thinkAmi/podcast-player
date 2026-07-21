package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode

/**
 * 再生終了後にどれを続けて鳴らすかの判断。
 *
 * 明示的なキューは持たない。いま画面に出ている(=フィルター適用後の)リストの並び順が、 そのまま再生順になる。フィルターが再生順の制御まで兼ねる構造。
 */
object PlaybackQueue {

    /**
     * [currentEpisodeId] の次に自動再生すべきエピソードを返す。
     *
     * 未DLのエピソードはスキップする。勝手にダウンロードを始めると通信量を消費するため、 自動継続の対象はDL済みのものだけに限る。該当がなければ null(=停止)。
     */
    fun nextAutoPlayable(episodes: List<Episode>, currentEpisodeId: Long): Episode? {
        val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex < 0) return null
        return episodes.asSequence().drop(currentIndex + 1).firstOrNull { it.downloaded }
    }

    /**
     * 選んだエピソードから始まる再生順を返す。
     *
     * 先頭は必ず選ばれたエピソード、以降はリスト順のDL済みだけ。これをそのまま再生キューに 渡すことで、「未DLはスキップし、自動でDLしない」という約束をプレイヤー任せにできる。
     * 選んだエピソードがDL済みでなければ何も再生しない(鳴らすファイルがない)。
     */
    fun playbackOrderFrom(episodes: List<Episode>, startEpisodeId: Long): List<Episode> {
        val startIndex = episodes.indexOfFirst { it.id == startEpisodeId }
        if (startIndex < 0 || !episodes[startIndex].downloaded) return emptyList()
        return episodes.drop(startIndex).filter { it.downloaded }
    }
}
