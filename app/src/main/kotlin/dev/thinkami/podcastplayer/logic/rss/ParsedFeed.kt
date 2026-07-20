package dev.thinkami.podcastplayer.logic.rss

/**
 * XMLから抽出しただけの、まだ解釈していないフィード。
 *
 * XMLの走査(data層)と、値の解釈・妥当性判断(logic層)を分けるための中間表現。この形にして おくことで、日付や長さの表記ゆれ、必須要素の欠落といった「世のRSSの汚さ」への対処を
 * 実機なしのJVMユニットテストで検証できる。
 */
data class ParsedFeed(
    val title: String?,
    val artworkUrl: String?,
    val items: List<ParsedItem>,
)

/** `<item>` 1件から抽出した生の文字列。数値・日付への変換はまだ行っていない。 */
data class ParsedItem(
    val guid: String?,
    val title: String?,
    val description: String?,
    val pubDate: String?,
    val enclosureUrl: String?,
    val enclosureLength: String?,
    val itunesDuration: String?,
)
