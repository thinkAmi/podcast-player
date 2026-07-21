package dev.thinkami.podcastplayer.data

import dev.thinkami.podcastplayer.logic.model.Episode
import dev.thinkami.podcastplayer.logic.model.PlayedSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * エピソードの状態(視聴済み・再生位置・DLファイル)の操作。
 *
 * 「削除してよいか」の判断は logic 層の ListeningRules が持ち、ここは実行だけを行う。
 */
interface EpisodeRepository {

    fun observeEpisode(episodeId: Long): Flow<Episode?>

    suspend fun findEpisode(episodeId: Long): Episode?

    suspend fun findEpisodes(feedId: Long): List<Episode>

    /** 視聴状態を変えるだけ。ファイル削除は行わない(手動操作には取り消しの猶予があるため)。 */
    suspend fun setPlayed(episodeId: Long, played: Boolean)

    /** 番組内を一括で視聴済み/未聴にし、操作前の状態を返す。 返り値をそのまま [restorePlayed] に渡せば取り消せる。 */
    suspend fun setPlayedForFeed(feedId: Long, played: Boolean): List<PlayedSnapshot>

    /** 一括操作の取り消し。 */
    suspend fun restorePlayed(snapshots: List<PlayedSnapshot>)

    /** 再生位置の保存。 */
    suspend fun savePosition(episodeId: Long, positionMs: Long)

    /** 再生が最後まで達したときの処理。視聴済みにし、条件を満たせば即座にファイルを削除する。 自動判定は誤発動の余地がないため猶予を置かない。 */
    suspend fun markPlaybackCompleted(episodeId: Long)

    /** 削除条件(視聴済み かつ DL済み かつ favorite でない)を満たすものだけファイルを消す。 手動で視聴済みにした場合は、取り消しの猶予が過ぎてからこれを呼ぶ。 */
    suspend fun deleteDownloadsIfEligible(episodeIds: List<Long>)
}
