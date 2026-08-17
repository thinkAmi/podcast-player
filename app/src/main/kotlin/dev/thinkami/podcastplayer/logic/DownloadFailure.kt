package dev.thinkami.podcastplayer.logic

/**
 * ダウンロードが失敗した理由の種別。
 *
 * 例外のメッセージをそのまま画面に出すと、URL 全文のような長い文字列が行に載り、しかも 表示の書式が data 層の例外メッセージに結合してしまう。行に出すのは種別だけにし、
 * 種別から文言を決める判断をここ(純粋な判断の層)に置く。
 */
sealed interface DownloadFailure {

    /** 取得を許可していない URL。再試行しても結果は変わらない。 */
    data object UnsupportedUrl : DownloadFailure

    /** サーバーがエラーステータスを返した。 */
    data class HttpStatus(val code: Int) : DownloadFailure

    /** 接続できない・切断された・時間切れ。 */
    data object Connection : DownloadFailure

    /** identity を要求したのに圧縮して返された応答。 */
    data object CompressedResponse : DownloadFailure

    /** 受信は済んだが保存を確定できなかった。再試行しても同じところで失敗する見込み。 */
    data object Save : DownloadFailure
}

/** 失敗の種別を行の 2 行目に出すための文言に変換する。 */
object DownloadFailurePresentation {

    /**
     * 行に出す短い理由。
     *
     * 長さは 2 行目に収まることを優先する。URL や例外メッセージは含めない。
     */
    fun label(failure: DownloadFailure): String =
        when (failure) {
            DownloadFailure.UnsupportedUrl -> "DL失敗(取得できないURL)"
            is DownloadFailure.HttpStatus -> "DL失敗(HTTP ${failure.code})"
            DownloadFailure.Connection -> "DL失敗(接続できず)"
            DownloadFailure.CompressedResponse -> "DL失敗(非対応の応答)"
            DownloadFailure.Save -> "DL失敗(保存できず)"
        }

    /**
     * 失敗した操作そのものに付ける名前(行のアイコンの読み上げ・統合エピソード画面のボタン)。
     *
     * 一覧の副題と別に用意するのは、こちらが「操作の名前」だからで、理由だけを述べる [label] とは 文体が違うため。ただし再試行を勧めるかどうかの規則は [suggestsRetry]
     * で共有する。 副題では案内を消したのにボタンでは促す、という食い違いを構造で防ぐ。
     */
    fun actionLabel(failure: DownloadFailure): String =
        if (suggestsRetry(failure)) "${label(failure)}。もう一度試す" else label(failure)

    /**
     * 再試行を勧めてよいか。
     *
     * 勧めないのは、もう一度同じ操作をしても同じ理由で失敗することが分かっている種別。 案内を出さないだけで再タップ自体は妨げない(操作を奪うと、配信側が直ったときに
     * やり直す手段がなくなるため)。
     */
    fun suggestsRetry(failure: DownloadFailure): Boolean =
        when (failure) {
            DownloadFailure.UnsupportedUrl -> false
            DownloadFailure.Save -> false
            is DownloadFailure.HttpStatus -> true
            DownloadFailure.Connection -> true
            DownloadFailure.CompressedResponse -> true
        }
}
