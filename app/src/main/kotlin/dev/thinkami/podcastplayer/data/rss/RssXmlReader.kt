package dev.thinkami.podcastplayer.data.rss

import dev.thinkami.podcastplayer.logic.rss.ParsedFeed
import dev.thinkami.podcastplayer.logic.rss.ParsedItem
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * RSS 2.0 (+ iTunes名前空間) のXMLを走査して、生の文字列を取り出すだけの層。
 *
 * 値の解釈(日付・長さ・必須要素の判断)は行わない。それは logic 層の [dev.thinkami.podcastplayer.logic.rss.RssInterpretation]
 * の責務で、この分離により 「世のRSSの汚さ」への対処をJVMユニットテストで検証できる。
 *
 * 名前空間処理は無効にして `itunes:duration` のような接頭辞付きの名前をそのまま照合する。 実在のフィードは名前空間宣言が不完全なことがあり、その方が壊れにくい。
 */
class RssXmlReader {

    fun read(xml: String): ParsedFeed {
        val parser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(StringReader(xml))

        var channelTitle: String? = null
        var artworkUrl: String? = null
        val items = mutableListOf<ParsedItem>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_ITEM -> items += readItem(parser)
                    TAG_ITUNES_IMAGE ->
                        artworkUrl = artworkUrl ?: parser.getAttributeValue(null, ATTR_HREF)
                    TAG_URL -> artworkUrl = artworkUrl ?: parser.nextText()
                    TAG_TITLE -> channelTitle = channelTitle ?: parser.nextText()
                    else -> Unit
                }
            }
            event = parser.next()
        }
        return ParsedFeed(title = channelTitle, artworkUrl = artworkUrl, items = items)
    }

    private fun readItem(parser: XmlPullParser): ParsedItem {
        val values = mutableMapOf<String, String>()
        var enclosureUrl: String? = null
        var enclosureLength: String? = null

        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == TAG_ITEM)) {
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == TAG_ENCLOSURE) {
                    enclosureUrl = parser.getAttributeValue(null, ATTR_URL)
                    enclosureLength = parser.getAttributeValue(null, ATTR_LENGTH)
                } else {
                    readTextInto(parser, values)
                }
            }
            event = parser.next()
        }

        return ParsedItem(
            guid = values[TAG_GUID],
            title = values[TAG_TITLE],
            // ショーノートは content:encoded に入るフィードもある。より情報量の多い方を優先する。
            description = values[TAG_CONTENT_ENCODED] ?: values[TAG_DESCRIPTION],
            pubDate = values[TAG_PUB_DATE],
            enclosureUrl = enclosureUrl,
            enclosureLength = enclosureLength,
            itunesDuration = values[TAG_ITUNES_DURATION],
        )
    }

    /** 関心のある要素のテキストだけを拾う。最初に現れた値を優先する。 */
    private fun readTextInto(parser: XmlPullParser, values: MutableMap<String, String>) {
        val name = parser.name
        if (name !in ITEM_TEXT_TAGS || values.containsKey(name)) return
        val text = parser.nextText()?.trim()
        if (!text.isNullOrEmpty()) values[name] = text
    }

    private companion object {
        const val TAG_ITEM = "item"
        const val TAG_TITLE = "title"
        const val TAG_GUID = "guid"
        const val TAG_DESCRIPTION = "description"
        const val TAG_CONTENT_ENCODED = "content:encoded"
        const val TAG_PUB_DATE = "pubDate"
        const val TAG_ENCLOSURE = "enclosure"
        const val TAG_ITUNES_DURATION = "itunes:duration"
        const val TAG_ITUNES_IMAGE = "itunes:image"
        const val TAG_URL = "url"
        const val ATTR_URL = "url"
        const val ATTR_HREF = "href"
        const val ATTR_LENGTH = "length"

        val ITEM_TEXT_TAGS =
            setOf(
                TAG_TITLE,
                TAG_GUID,
                TAG_DESCRIPTION,
                TAG_CONTENT_ENCODED,
                TAG_PUB_DATE,
                TAG_ITUNES_DURATION,
            )
    }
}
