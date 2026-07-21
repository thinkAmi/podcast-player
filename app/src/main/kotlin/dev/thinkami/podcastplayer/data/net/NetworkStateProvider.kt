package dev.thinkami.podcastplayer.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 回線が従量制かどうかの判定。
 *
 * Wi-Fi のときは確認を挟まず即DLし、モバイル回線のときだけ確認ダイアログを出すために使う。 日常の操作(自宅Wi-Fiで朝まとめてDL)を邪魔せず、外での誤タップだけを止めるのが狙い。
 */
class NetworkStateProvider(private val context: Context) {

    fun isMetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities =
            manager.activeNetwork?.let { network -> manager.getNetworkCapabilities(network) }
        // 判定できないときは従量制とみなす。誤って通信量を使うより、確認を1回挟む方が安全。
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.not()
            ?: true
    }
}
