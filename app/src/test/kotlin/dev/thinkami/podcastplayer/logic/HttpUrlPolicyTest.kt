package dev.thinkami.podcastplayer.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpUrlPolicyTest {

    @Test
    fun `httpsは許可する`() {
        assertTrue(HttpUrlPolicy.isAllowed("https://example.com/rss"))
    }

    @Test
    fun `スキームの大文字小文字は問わない`() {
        assertTrue(HttpUrlPolicy.isAllowed("HTTPS://example.com/rss"))
        assertTrue(HttpUrlPolicy.isAllowed("Https://example.com/rss"))
    }

    @Test
    fun `前後の空白は無視して判定する`() {
        assertTrue(HttpUrlPolicy.isAllowed("  https://example.com/rss  "))
    }

    @Test
    fun `fileスキームは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("file:///sdcard/x.xml"))
    }

    @Test
    fun `loopback以外の平文HTTPは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("http://example.com/rss"))
    }

    @Test
    fun `未知のスキームは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("ftp://example.com/rss"))
        assertFalse(HttpUrlPolicy.isAllowed("jar:file:///x.jar!/y"))
        assertFalse(HttpUrlPolicy.isAllowed("content://media/external/audio/1"))
    }

    @Test
    fun `スキームのない文字列は拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("example.com/rss"))
        assertFalse(HttpUrlPolicy.isAllowed(""))
        assertFalse(HttpUrlPolicy.isAllowed("   "))
    }

    @Test
    fun `loopback宛の平文HTTPは計装テストのために許可する`() {
        assertTrue(HttpUrlPolicy.isAllowed("http://127.0.0.1:8080/feed.xml"))
        assertTrue(HttpUrlPolicy.isAllowed("http://127.0.0.1/feed.xml"))
    }

    @Test
    fun `userinfoでloopbackを詐称するURLは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("http://127.0.0.1@evil.example.com/rss"))
        assertFalse(HttpUrlPolicy.isAllowed("http://127.0.0.1:pass@evil.example.com/rss"))
    }

    @Test
    fun `loopbackに似た別ホストは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("http://127.0.0.1.evil.example.com/rss"))
        assertFalse(HttpUrlPolicy.isAllowed("http://localhost:8080/feed.xml"))
    }

    @Test
    fun `httpsなら空白を含む汚いURLでも通す`() {
        // 実在フィードには未エンコードの文字を含む enclosure URL があり、
        // 厳格なURLパースで弾くと今まで取り込めていたものが取り込めなくなる。
        assertTrue(HttpUrlPolicy.isAllowed("https://example.com/ep 01.mp3"))
        assertTrue(HttpUrlPolicy.isAllowed("https://example.com/日本語.mp3"))
        assertTrue(HttpUrlPolicy.isAllowed("https://example.com/a[1].mp3"))
    }

    @Test
    fun `パースできない平文HTTPは拒否する`() {
        assertFalse(HttpUrlPolicy.isAllowed("http://127.0.0.1/ep 01.mp3"))
    }
}
