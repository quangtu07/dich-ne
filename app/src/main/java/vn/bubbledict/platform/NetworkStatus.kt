package vn.bubbledict.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * BUG-16 / spec 1.16: phân biệt **máy mất mạng** với **nguồn dịch chết**.
 *
 * - Mất mạng thật -> pipeline trả E3a/E3b ("Bật mạng…").
 * - Có mạng nhưng cả chain keyless hỏng hạ tầng -> E28 ("Nguồn dịch miễn phí tạm không dùng được").
 *
 * Dùng `NET_CAPABILITY_VALIDATED` (Android đã xác nhận ra được Internet thật), không chỉ
 * `CONNECTED` — tránh trường hợp cắm Wi-Fi không có mạng mà vẫn báo "có mạng".
 * Không cần quyền `ACCESS_NETWORK_STATE`? Có: đọc ConnectivityManager cần quyền thường
 * (`android.permission.ACCESS_NETWORK_STATE`) — đã khai trong AndroidManifest.
 */
class NetworkStatus(context: Context) {

    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** true = máy đang có kết nối Internet đã xác thực. Lỗi đọc trạng thái -> coi như có mạng. */
    fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}