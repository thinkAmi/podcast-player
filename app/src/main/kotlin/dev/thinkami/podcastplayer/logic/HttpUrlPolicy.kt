package dev.thinkami.podcastplayer.logic

import java.net.URI
import java.net.URISyntaxException

/**
 * 取得してよいURLかどうかの判断。
 *
 * 許可するのは https と、計装テスト用の loopback (`http://127.0.0.1`) だけ。これは単なる方針ではなく、 `HttpURLConnection` へのキャストが
 * `file:` などで `ClassCastException` になる(IOException ではないため 上位の catch を素通りしてアプリが落ちる)ことを防ぐための検査でもある。
 *
 * 判断の入口は購読URL・フィード内の artworkUrl / enclosureUrl の3つあるが、検査は data 層の HttpFetcher
 * 1箇所で強制する。ここはその「判断」だけを持つ。
 */
object HttpUrlPolicy {

    /** 計装テストの Fake サーバーだけが使う。本番ビルドは平文HTTPをOSが遮断するため到達しない。 */
    private const val LOOPBACK_HOST = "127.0.0.1"

    private const val HTTPS_PREFIX = "https://"
    private const val HTTP_PREFIX = "http://"

    /**
     * 取得してよいURLか。
     *
     * https の判定にURLパースを使わないのは、実在のフィードには空白や非ASCIIを含む enclosure URL があり、
     * 厳格なパースを通すと今まで取り込めていたものを弾いてしまうため。スキームは最初の `:` までで決まるので、 前置検査で過不足なく判定できる。
     */
    fun isAllowed(url: String): Boolean {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true) -> true
            trimmed.startsWith(HTTP_PREFIX, ignoreCase = true) -> isLoopback(trimmed)
            else -> false
        }
    }

    /**
     * loopback 宛の平文HTTPか。
     *
     * ここだけはホストを正しく取り出す必要がある。`http://127.0.0.1@evil.example.com/` のような userinfo
     * を使った詐称を前置検査では見抜けないため。[URI] のホストは userinfo を含まない。
     */
    private fun isLoopback(url: String): Boolean =
        try {
            URI(url).host == LOOPBACK_HOST
        } catch (_: URISyntaxException) {
            false
        }
}
