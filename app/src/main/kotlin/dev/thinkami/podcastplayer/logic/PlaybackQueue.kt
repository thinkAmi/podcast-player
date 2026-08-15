package dev.thinkami.podcastplayer.logic

import dev.thinkami.podcastplayer.logic.model.Episode

/**
 * 再生終了後にどれを続けて鳴らすかの判断。
 *
 * 明示的なキューは持たない。いま画面に出ている(=フィルター適用後の)リストを、公開の古い順へ 並べ替えたものがそのまま再生順になる。フィルターが再生対象の制御を、並びの反転が再生順の制御を
 * それぞれ兼ねる構造。
 *
 * 一覧は新しい順(`publishedAt` の降順)で表示される。聴くときは残っている中で一番古い回から
 * 新しい回へ進むため、再生順は一覧の**逆順**になる。この対応関係は一覧の並びが降順であることに 依存しており、一覧の並びを変えれば再生順もそれに追随する。
 */
object PlaybackQueue {

    /**
     * 選んだエピソードから始まる再生順を返す。
     *
     * 先頭は必ず選ばれたエピソード、以降はそれより新しいDL済みだけが古い順に続く。これをそのまま
     * 再生キューに渡すことで、「未DLはスキップし、自動でDLしない」という約束をプレイヤー任せにできる。
     * 選んだエピソードより古いものは含めない。開始点は常に「残っている中で一番古い回」であり、 そこから遡る先はないため。
     *
     * 選んだエピソードがDL済みでなければ何も再生しない(鳴らすファイルがない)。
     */
    fun playbackOrderFrom(episodes: List<Episode>, startEpisodeId: Long): List<Episode> {
        val startIndex = episodes.indexOfFirst { it.id == startEpisodeId }
        if (startIndex < 0 || !episodes[startIndex].downloaded) return emptyList()
        return episodes.take(startIndex + 1).reversed().filter { it.downloaded }
    }
}
