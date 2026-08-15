package dev.thinkami.podcastplayer.logic

/**
 * アートワークを「どう見せるか」の判断。
 *
 * 画像そのものを読む・描くのは data/ui の仕事で、ここにあるのは「どこまで縮めるか」「画像が無い ときに何の文字を出すか」という決定だけ。BitmapFactory
 * を触らずに済ませることで、端ケース (巨大画像・空タイトル・絵文字始まり)を高速な JVM ユニットテストで固められる。
 */
object ArtworkPresentation {

    /** タイトルから文字を拾えないときに出す代替。 */
    const val FALLBACK_MONOGRAM = "?"

    /**
     * BitmapFactory.Options.inSampleSize に渡す縮小率。
     *
     * 表示に必要なのは目標ピクセルまでで、3000px 角の原寸(1枚 36MB)を展開する理由はない。 デコード後も目標サイズを下回らない範囲で最も粗くする =
     * 条件を満たす最大の2の冪を返す。
     *
     * 寸法が読めなかった場合(BitmapFactory は失敗時に -1 を返す)や目標が不正な場合は、 縮小しない(1)を返して呼び出し側の分岐を増やさない。
     */
    fun sampleSizeFor(sourceWidth: Int, sourceHeight: Int, targetPx: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetPx <= 0) return 1
        // 短辺を基準にする。長辺基準だと非正方形の画像で短辺が目標を割り、粗く見える。
        val shorterSide = minOf(sourceWidth, sourceHeight)
        var sampleSize = 1
        while (shorterSide / (sampleSize * 2) >= targetPx) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * アートワークが無い番組に出す1文字。
     *
     * 取得できない番組は設計上ふつうに存在する(URL 未申告・取得失敗・キャッシュ破損)ため、 これは異常系ではなく通常の表示経路。どんなタイトルでも例外を投げずに1文字を返す。
     *
     * 絵文字や国旗はサロゲートペアで2 Char になるため、Char ではなくコードポイント単位で切り出す。 片割れだけを表示すると豆腐になる。
     */
    fun monogramFor(title: String): String {
        val trimmed = title.trimStart()
        if (trimmed.isEmpty()) return FALLBACK_MONOGRAM
        val firstCodePointLength = Character.charCount(trimmed.codePointAt(0))
        return trimmed.substring(0, firstCodePointLength).uppercase()
    }
}
