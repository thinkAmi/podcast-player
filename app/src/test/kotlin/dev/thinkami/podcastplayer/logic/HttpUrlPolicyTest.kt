package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpUrlPolicyTest {

    @Test
    fun `httpsはそのまま使う`() {
        assertEquals(
            "https://example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("https://example.com/rss"),
        )
    }

    @Test
    fun `スキームの大文字小文字は問わない`() {
        assertEquals(
            "HTTPS://example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("HTTPS://example.com/rss"),
        )
        assertEquals(
            "Https://example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("Https://example.com/rss"),
        )
    }

    @Test
    fun `前後の空白は落として使う`() {
        assertEquals(
            "https://example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("  https://example.com/rss  "),
        )
    }

    @Test
    fun `fileスキームは取得しない`() {
        assertNull(HttpUrlPolicy.resolveFetchUrl("file:///sdcard/x.xml"))
    }

    @Test
    fun `loopback以外の平文HTTPはhttpsへ書き換える`() {
        assertEquals(
            "https://example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("http://example.com/rss"),
        )
    }

    @Test
    fun `書き換えるのは先頭のスキームだけ`() {
        // クエリや大文字スキームを巻き込むと、配信側のURLとして別物になってしまう。
        assertEquals(
            "https://Example.com/p?x=http://y",
            HttpUrlPolicy.resolveFetchUrl("HTTP://Example.com/p?x=http://y"),
        )
        assertEquals(
            "https://example.com/media/are_170701_b.mp3",
            HttpUrlPolicy.resolveFetchUrl("http://example.com/media/are_170701_b.mp3"),
        )
    }

    @Test
    fun `未知のスキームは取得しない`() {
        assertNull(HttpUrlPolicy.resolveFetchUrl("ftp://example.com/rss"))
        assertNull(HttpUrlPolicy.resolveFetchUrl("jar:file:///x.jar!/y"))
        assertNull(HttpUrlPolicy.resolveFetchUrl("content://media/external/audio/1"))
    }

    @Test
    fun `スキームのない文字列は取得しない`() {
        assertNull(HttpUrlPolicy.resolveFetchUrl("example.com/rss"))
        assertNull(HttpUrlPolicy.resolveFetchUrl(""))
        assertNull(HttpUrlPolicy.resolveFetchUrl("   "))
    }

    @Test
    fun `loopback宛の平文HTTPは計装テストのために書き換えない`() {
        assertEquals(
            "http://127.0.0.1:8080/feed.xml",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1:8080/feed.xml"),
        )
        assertEquals(
            "http://127.0.0.1/feed.xml",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1/feed.xml"),
        )
    }

    @Test
    fun `userinfoでloopbackを詐称するURLは書き換えの対象になる`() {
        // loopback の例外に紛れ込ませようとしても平文では出ない。
        assertEquals(
            "https://127.0.0.1@evil.example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1@evil.example.com/rss"),
        )
        assertEquals(
            "https://127.0.0.1:pass@evil.example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1:pass@evil.example.com/rss"),
        )
    }

    @Test
    fun `loopbackに似た別ホストは書き換えの対象になる`() {
        assertEquals(
            "https://127.0.0.1.evil.example.com/rss",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1.evil.example.com/rss"),
        )
        assertEquals(
            "https://localhost:8080/feed.xml",
            HttpUrlPolicy.resolveFetchUrl("http://localhost:8080/feed.xml"),
        )
    }

    @Test
    fun `空白を含む汚いURLでも通す`() {
        // 実在フィードには未エンコードの文字を含む enclosure URL があり、
        // 厳格なURLパースで弾くと今まで取り込めていたものが取り込めなくなる。
        assertEquals(
            "https://example.com/ep 01.mp3",
            HttpUrlPolicy.resolveFetchUrl("https://example.com/ep 01.mp3"),
        )
        assertEquals(
            "https://example.com/日本語.mp3",
            HttpUrlPolicy.resolveFetchUrl("https://example.com/日本語.mp3"),
        )
        assertEquals(
            "https://example.com/a[1].mp3",
            HttpUrlPolicy.resolveFetchUrl("http://example.com/a[1].mp3"),
        )
    }

    @Test
    fun `パースできない平文HTTPはloopbackとみなさず書き換える`() {
        // ホストを取り出せない以上 loopback の確証がない。例外を広げるより書き換える側に倒す。
        assertEquals(
            "https://127.0.0.1/ep 01.mp3",
            HttpUrlPolicy.resolveFetchUrl("http://127.0.0.1/ep 01.mp3"),
        )
    }
}
