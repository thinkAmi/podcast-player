package dev.thinkami.podcastplayer.ui

import androidx.core.text.HtmlCompat

/**
 * ショーノートを読める平文に直す。
 *
 * フィードの description は HTML で書かれていることが多く、そのまま出すと `<p>` や `<a href="...">` が本文に混ざって読めない。HTML の解釈は OS
 * 標準の [HtmlCompat] に任せ、 ライブラリは追加しない。リンクは URL ではなく文字列として残る(MVP ではタップ不要)。
 */
fun showNotesToPlainText(rawHtml: String?): String? {
    val raw = rawHtml?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val text = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    return text.ifEmpty { null }
}
