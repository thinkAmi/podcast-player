package dev.thinkami.podcastplayer.logic

import java.net.URI
import java.net.URISyntaxException

/**
 * 取得に使うURLの決定。
 *
 * 平文で取りにいくことは決してない。https はそのまま、loopback 以外の平文HTTPはスキームだけを https へ 書き換える。それ以外(`file:`
 * や未知スキーム)は取得しない。
 *
 * 書き換えるのは、2021年以前に配信された回の enclosure が `http://` のままのフィードが実在し、 同じホストが https
 * でも同じファイルを返すため。拒否したままではその回を永久に取り込めない。 平文HTTPを許可する(cleartext 許可)という選択肢は採らない。書き換え後に https 非対応であれば
 * 通常の通信エラーとして失敗すればよく、平文で流す理由にはならないため。
 *
 * 取得しないURLを弾くのは単なる方針ではなく、`HttpURLConnection` へのキャストが `file:` などで `ClassCastException`
 * になる(IOException ではないため上位の catch を素通りしてアプリが落ちる)ことを 防ぐための検査でもある。
 *
 * 判断の入口は購読URL・フィード内の artworkUrl / enclosureUrl の3つあるが、適用は data 層の HttpFetcher
 * 1箇所で強制する。ここはその「判断」だけを持つ。
 */
object HttpUrlPolicy {

    /** 計装テストの Fake サーバーだけが使う。本番ビルドは平文HTTPをOSが遮断するため到達しない。 */
    private const val LOOPBACK_HOST = "127.0.0.1"

    private const val HTTPS_PREFIX = "https://"
    private const val HTTP_PREFIX = "http://"

    /**
     * 実際に取得しにいくURL。取得しないURLでは null を返す。
     *
     * 判定にURLパースを使わないのは、実在のフィードには空白や非ASCIIを含む enclosure URL があり、
     * 厳格なパースを通すと今まで取り込めていたものを弾いてしまうため。スキームは最初の `:` までで決まるので、前置検査で過不足なく判定できる。書き換えも同じ理由で先頭のスキームだけを
     * 差し替え、ホスト・パス・クエリには触れない(クエリに現れる `http://` を巻き込まないため)。
     *
     * 戻り値は取得に使うだけで、DBに保存されたURLは書き換えない。配信側が後日 https に直しても 同じエピソードとして扱えるようにするため。
     */
    fun resolveFetchUrl(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true) -> trimmed
            !trimmed.startsWith(HTTP_PREFIX, ignoreCase = true) -> null
            isLoopback(trimmed) -> trimmed
            else -> HTTPS_PREFIX + trimmed.substring(HTTP_PREFIX.length)
        }
    }

    /**
     * loopback 宛の平文HTTPか。
     *
     * ここだけはホストを正しく取り出す必要がある。`http://127.0.0.1@evil.example.com/` のような userinfo
     * を使った詐称を前置検査では見抜けないため。[URI] のホストは userinfo を含まない。 詐称と判定されたURLは平文のまま通るのではなく https
     * へ書き換えられる(外へ平文で出ることはない)。
     */
    private fun isLoopback(url: String): Boolean =
        try {
            URI(url).host == LOOPBACK_HOST
        } catch (_: URISyntaxException) {
            false
        }
}
