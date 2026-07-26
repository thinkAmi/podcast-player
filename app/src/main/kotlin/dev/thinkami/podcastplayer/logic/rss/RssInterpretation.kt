package dev.thinkami.podcastplayer.logic.rss

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 抽出済みの文字列を解釈してドメインの値に変換する。
 *
 * 世のポッドキャストRSSは仕様違反や表記ゆれが多い。ここでの原則は「壊れた項目は捨てて残りの 取り込みを続ける」。1件の不正なitemでフィード全体の更新が失敗してはならない。
 */
object RssInterpretation {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val DURATION_PARTS_MMSS = 2
    private const val DURATION_PARTS_HHMMSS = 3

    /**
     * pubDate の解釈。RSS 2.0 は RFC 822 を要求するが、実際には亜種が流通している。 RFC_1123_DATE_TIME
     * は曜日と秒が最初からオプショナルで、月名・曜日名も ロケール非依存(英語固定)のため、「曜日なし」「秒なし」の亜種もこれ1つで解釈できる。 ofPattern
     * による自前のフォールバックを足さないこと(ロケール依存になる上、 RFC_1123_DATE_TIME が解釈できる書式の部分集合にしかならない)。 どれにも当てはまらなければ
     * null。
     */
    private val dateFormatters: List<DateTimeFormatter> =
        listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            // ISO 8601 を使うフィードもある
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        )

    fun parsePublishedAt(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return dateFormatters.firstNotNullOfOrNull { formatter -> tryParseDate(text, formatter) }
    }

    private fun tryParseDate(text: String, formatter: DateTimeFormatter): Long? =
        try {
            ZonedDateTime.parse(text, formatter).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }

    /**
     * itunes:duration の解釈。"HH:MM:SS" / "MM:SS" / 秒数のいずれかで書かれる。 解釈できなければ
     * null(長さ不明として扱い、視聴済みの自動判定は行わない)。
     */
    fun parseDurationMs(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split(":").map { it.trim() }
        val seconds =
            when (parts.size) {
                1 -> parts[0].toLongOrNull()
                DURATION_PARTS_MMSS -> combine(minutes = parts[0], seconds = parts[1])
                DURATION_PARTS_HHMMSS ->
                    combine(hours = parts[0], minutes = parts[1], seconds = parts[2])
                else -> null
            }
        return seconds?.takeIf { it > 0L }?.times(MILLIS_PER_SECOND)
    }

    private fun combine(hours: String = "0", minutes: String, seconds: String): Long? {
        val h = hours.toLongOrNull()
        val m = minutes.toLongOrNull()
        val s = seconds.toLongOrNull()
        return if (h == null || m == null || s == null) {
            null
        } else {
            h * SECONDS_PER_HOUR + m * SECONDS_PER_MINUTE + s
        }
    }

    fun parseSizeBytes(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it > 0L }

    /**
     * 取り込み可能なitemかどうかを判断し、可能なら正規化した形で返す。
     *
     * 音声のURLがなければ再生もDLもできないので捨てる。guid を持たないフィードもあるため、 その場合は enclosure URL
     * を代用の一意キーにする(同じ音声=同じエピソードとみなせる)。
     */
    fun normalize(item: ParsedItem): NormalizedItem? {
        val enclosureUrl = item.enclosureUrl?.trim().orEmpty()
        if (enclosureUrl.isEmpty()) return null
        val guid = item.guid?.trim()?.takeIf { it.isNotEmpty() } ?: enclosureUrl
        return NormalizedItem(
            guid = guid,
            title = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: UNTITLED,
            showNotes = item.description?.trim()?.takeIf { it.isNotEmpty() },
            publishedAtEpochMillis = parsePublishedAt(item.pubDate) ?: 0L,
            durationMs = parseDurationMs(item.itunesDuration),
            enclosureUrl = enclosureUrl,
            enclosureSizeBytes = parseSizeBytes(item.enclosureLength),
        )
    }

    /** 壊れたitemを捨てつつ、取り込めるものだけを返す。 */
    fun normalizeAll(items: List<ParsedItem>): List<NormalizedItem> = items.mapNotNull(::normalize)

    const val UNTITLED = "(タイトルなし)"
}

/** 取り込み可能と判断され、値の解釈まで済んだitem。 */
data class NormalizedItem(
    val guid: String,
    val title: String,
    val showNotes: String?,
    val publishedAtEpochMillis: Long,
    val durationMs: Long?,
    val enclosureUrl: String,
    val enclosureSizeBytes: Long?,
)
