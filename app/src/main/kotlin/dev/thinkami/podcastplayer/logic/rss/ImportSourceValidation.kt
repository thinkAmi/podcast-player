package dev.thinkami.podcastplayer.logic.rss

/**
 * 既存購読へエピソードを取り込んでよいかの判断。
 *
 * 取り込みは「どの購読に取り込むか」と「どのXMLを取り込むか」を別々に指定するため、両者の 取り違えが起こり得る。それを機械的に弾くのがここ。
 *
 * 判断材料は、XMLが `<source url>` で申告する出典フィードと、DBに保存されている購読URLの2つ。
 * 前者はXML、後者はDBという**独立した出所**を持つので、指定の取り違えは必ず不一致として現れる。
 *
 * 配信ドメインの一致で代用しないこと。音源をCDNで配る番組では正当な取り込みまで弾き、逆に 同じホスティングを使う別番組同士は素通りしてしまう(危険な場面ほど効かない)。
 */
object ImportSourceValidation {

    /**
     * 取り込みの可否。
     *
     * 1件でも申告のないitem・食い違う申告があれば全体を拒否する(部分的な取り込みはしない)。 取り込むものが1件もないXMLも拒否する。空の集合に対して「全件が一致した」と判定すると、
     * 無関係な空のRSSが検査を素通りしてしまうため。
     */
    fun validate(sourceUrls: List<String?>, subscribedFeedUrl: String): ImportSourceVerdict {
        val declared = sourceUrls.map { it?.trim().orEmpty() }
        val distinct = declared.distinct()
        val subscribed = subscribedFeedUrl.trim()
        // 申告なしを先に弾くので、以降の分岐で distinct が空になることはない。
        return when {
            declared.isEmpty() || declared.any(String::isEmpty) -> ImportSourceVerdict.Undeclared
            distinct.size > 1 -> ImportSourceVerdict.Mixed(distinct)
            distinct.single() == subscribed -> ImportSourceVerdict.Allowed
            else ->
                ImportSourceVerdict.Mismatched(
                    declared = distinct.single(),
                    subscribed = subscribed,
                )
        }
    }
}

/** [ImportSourceValidation] の判定結果。拒否の理由は利用者に伝えるため区別して持つ。 */
sealed interface ImportSourceVerdict {

    /** 全itemが取り込み先と同じ出典を申告している。 */
    data object Allowed : ImportSourceVerdict

    /** 出典を申告しているitemが1件もない(取り込み用に作られたXMLではない)。 */
    data object Undeclared : ImportSourceVerdict

    /** itemごとに違う出典を申告している(複数のXMLを継ぎ足した等)。 */
    data class Mixed(val declaredUrls: List<String>) : ImportSourceVerdict

    /** 申告された出典が取り込み先の購読と違う(指定の取り違え)。 */
    data class Mismatched(val declared: String, val subscribed: String) : ImportSourceVerdict
}
