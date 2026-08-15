package dev.thinkami.podcastplayer.logic

/**
 * エピソードに対していま差し出すアクション。
 *
 * 一覧の行アイコンと統合エピソード画面のアクション領域は、見た目の大きさが違うだけで 同じ判断に従う。両者がそれぞれに条件分岐を持つと片方だけ直す事故が起きるため、 判断はここに一本化する。
 */
enum class EpisodeAction {
    /** ダウンロード実行中。進み具合を出す(操作は受け付けない)。 */
    DOWNLOADING,

    /** 現在のエピソード。開始ではなく再生/一時停止の切り替え。 */
    TOGGLE_PLAY_PAUSE,

    /** DL済みで、いま鳴っているものではない。ここから再生を始められる。 */
    PLAY,

    /** 直前のダウンロードが失敗している。もう一度試す。 */
    RETRY_DOWNLOAD,

    /** 未DL。ダウンロードから始める。 */
    DOWNLOAD,
}

object EpisodeActions {

    /**
     * エピソードの状態から差し出すアクションを決める。
     *
     * 判断材料は真偽値だけにしてある。ダウンロードの進み具合を表す型は data 層のものであり、 純粋な判断だけを置くこの層からは触れないため。
     *
     * 優先順位には理由がある。実行中の表示は他のどれよりも優先する(進行中に再生や再DLを 促さない)。現在のエピソードはDL済みでもあるので、開始ではなくトグルとして扱うために
     * DL済みより先に判定する。失敗表示は未DLの一種だが、原因が伝わるよう素の未DLと区別する。
     */
    fun actionFor(
        isDownloading: Boolean,
        isCurrent: Boolean,
        isDownloaded: Boolean,
        hasFailedDownload: Boolean,
    ): EpisodeAction =
        when {
            isDownloading -> EpisodeAction.DOWNLOADING
            isCurrent -> EpisodeAction.TOGGLE_PLAY_PAUSE
            isDownloaded -> EpisodeAction.PLAY
            hasFailedDownload -> EpisodeAction.RETRY_DOWNLOAD
            else -> EpisodeAction.DOWNLOAD
        }
}
