package dev.thinkami.podcastplayer.ui

/**
 * 取り消し猶予つきの操作の共通形。
 *
 * DLファイルの削除は取り消せないため、削除を伴う手動操作はどれも「スナックバーを [UNDO_WINDOW_MS] 出す → 押されなければ削除判定にかける」という同じレールに乗せる。
 * 取り消したときに何をどう戻すかだけが操作ごとに違う([UndoHolder.undo] が型で分岐する)。
 */
sealed interface UndoableChange {

    /** スナックバーに出す文言。 */
    val message: String

    /** 猶予が過ぎたら削除判定にかけるエピソード。 */
    val affectedEpisodeIds: List<Long>

    companion object {
        /** 取り消しを受け付ける時間。 */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/**
 * ★解除(favorite を外す)の、取り消し猶予つきの表現。
 *
 * 記録するのは「保持していたファイルが不要になった」★解除だけ。削除を伴わない付け外しは ★アイコンの再タップで戻ればよく、このレールには乗せない。 取り消しが復元するのは favorite
 * だけで、視聴状態には触れない。
 */
data class UndoableFavoriteChange(val episodeId: Long) : UndoableChange {

    override val message: String
        get() = "★を外しました。ダウンロードを削除します"

    override val affectedEpisodeIds: List<Long>
        get() = listOf(episodeId)
}
