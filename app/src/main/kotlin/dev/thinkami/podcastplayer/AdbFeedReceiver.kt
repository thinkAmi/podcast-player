package dev.thinkami.podcastplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.thinkami.podcastplayer.data.FeedRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * PC から購読を登録するための受け口。
 *
 * マニフェストで `exported="false"` にしてあるため、届くのは同一 uid からの broadcast だけ。 つまり `adb shell run-as <pkg> am
 * broadcast --user 0 ...` を打てる「USB デバッグを許可した PC」 だけがこの経路を使える。端末内の他アプリからは OS
 * が配達を拒否する。信頼境界は計装テストと同じで、 ソースコード内の秘密や隠蔽には依存していない。
 *
 * この経路のために UI や設定は増やさない。受け取った URL を画面からの登録とまったく同じ [FeedRepository.subscribe] に渡すだけで、判断は何も持たない。
 *
 * `import_url` が添えられていたときだけ、既存購読へのエピソード取り込み ([FeedRepository.importEpisodes]) に切り替わる。入口を分けないのは、PC
 * から届く経路を1つに保つため。
 */
class AdbFeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val feedUrl = intent.getStringExtra(EXTRA_FEED_URL)?.trim().orEmpty()
        if (feedUrl.isEmpty()) {
            setResult(RESULT_IGNORED, "feed_url が指定されていません", null)
            return
        }
        val importUrl = intent.getStringExtra(EXTRA_IMPORT_URL)?.trim().orEmpty()

        val repository = context.appContainer.feedRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val outcome =
                if (importUrl.isEmpty()) {
                    subscribe(repository, feedUrl)
                } else {
                    import(repository, feedUrl, importUrl)
                }
            pending.setResult(outcome.code, outcome.message, null)
            pending.finish()
        }
    }

    /**
     * 画面からの登録と同じ例外を、同じ粒度で扱う。
     *
     * Receiver には画面がなく失敗を表示できないため、理由は結果データとして PC 側へ返す。握りつぶさない。
     *
     * `am broadcast` は FLAG_RECEIVER_FOREGROUND を立てるため、Receiver の実行には約10秒の制限がある。
     * フィードの取得が遅い場合に制限へ達して ANR 扱いになることを避けるため、それより短い時間で区切る。
     */
    private suspend fun subscribe(repository: FeedRepository, feedUrl: String): Outcome =
        runWithBudget("$feedUrl の登録") {
            repository.subscribe(feedUrl)
            Outcome(RESULT_SUBSCRIBED, "登録しました: $feedUrl")
        }

    /** 取り込みも登録と同じ扱い。拒否の理由(出典の不一致・未購読など)はそのまま PC 側へ返す。 */
    private suspend fun import(
        repository: FeedRepository,
        feedUrl: String,
        importUrl: String,
    ): Outcome =
        runWithBudget("$importUrl の取り込み") {
            val result = repository.importEpisodes(feedUrl, importUrl)
            Outcome(
                RESULT_IMPORTED,
                "取り込みました: ${result.total}件(新規 ${result.added}件)-> $feedUrl",
            )
        }

    private suspend fun runWithBudget(what: String, action: suspend () -> Outcome): Outcome =
        try {
            withTimeout(SUBSCRIBE_BUDGET_MS) { action() }
        } catch (e: TimeoutCancellationException) {
            Outcome(RESULT_FAILED, "時間内に完了しませんでした(${e.message}): $what")
        } catch (e: IOException) {
            Outcome(RESULT_FAILED, e.message ?: "フィードを取得できませんでした")
        } catch (e: IllegalStateException) {
            Outcome(RESULT_FAILED, e.message ?: "処理できませんでした")
        } catch (e: IllegalArgumentException) {
            Outcome(RESULT_FAILED, e.message ?: "処理できませんでした")
        }

    private class Outcome(val code: Int, val message: String)

    companion object {
        /** PC 側スクリプトと共有する契約。変更したら `scripts/add-feed.sh` も直すこと。 */
        const val EXTRA_FEED_URL = "feed_url"

        /**
         * 既存購読へ取り込むエピソードの入ったXMLのURL。`scripts/import-episodes.sh` と共有する契約。
         *
         * これが無い(または空白だけの)broadcast は従来どおり購読登録として扱う。
         */
        const val EXTRA_IMPORT_URL = "import_url"

        /**
         * 結果コード。`am broadcast` は配達されなかった場合でも 0 を返すため、0 以外を返すこと自体が 「届いた」ことの証明になる。PC
         * 側スクリプトはこれを見て配達を判定する。
         */
        const val RESULT_SUBSCRIBED = 1
        const val RESULT_FAILED = 2
        const val RESULT_IGNORED = 3
        const val RESULT_IMPORTED = 4

        private const val SUBSCRIBE_BUDGET_MS = 8_000L
    }
}
