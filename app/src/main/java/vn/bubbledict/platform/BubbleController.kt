package vn.bubbledict.platform

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import vn.bubbledict.R

/**
 * API cho UI (FE-05 Settings) bật/tắt bong bóng (BE-13).
 *
 * - Kiểm quyền overlay + thông báo trước khi bật; thiếu quyền thì trả [StartResult] để UI
 *   mở màn giải thích (E7: từ chối quyền không crash, F1 vẫn dùng được).
 * - Bật/tắt service, vị trí bong bóng lưu trong DataStore qua [BubbleService].
 */
class BubbleController(private val context: Context) {

    enum class StartResult { STARTED, NEEDS_OVERLAY_PERMISSION, NEEDS_NOTIFICATION_PERMISSION, ALREADY_RUNNING }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun notificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }

    fun isRunning(): Boolean = BubbleService.isRunning

    fun start(): StartResult {
        if (!canDrawOverlays()) return StartResult.NEEDS_OVERLAY_PERMISSION
        if (!notificationsGranted()) return StartResult.NEEDS_NOTIFICATION_PERMISSION
        if (BubbleService.isRunning) return StartResult.ALREADY_RUNNING
        val intent = Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_START)
        context.startForegroundServiceCompat(intent)
        return StartResult.STARTED
    }

    fun stop() {
        context.startService(Intent(context, BubbleService::class.java).setAction(BubbleService.ACTION_STOP))
    }

    /**
     * Ẩn/hiện bong bóng khi màn phủ (chọn vùng) chiếm màn hình — Designer §4.1.
     *
     * - `visible = false`: ẩn tạm bằng INVISIBLE, không đổi kích thước/vị trí/busy.
     * - `visible = true`: hiện lại đúng vị trí đã lưu, chỉ khi `bubbleEnabled` (guard như
     *   [ACTION_SHOW_BUBBLE]); busy không được phép tự đưa bong bóng lên khi đang bị ẩn.
     */
    fun setVisible(visible: Boolean) {
        val action = if (visible) BubbleService.ACTION_SHOW_BUBBLE else BubbleService.ACTION_HIDE_BUBBLE
        runCatching {
            context.startService(Intent(context, BubbleService::class.java).setAction(action))
        }
    }

    /**
     * AC31 — bật/tắt trạng thái đang xử lý trên bong bóng (vòng cung quay).
     * Không throw khi service chưa chạy: chỉ trạng thái UI, không ảnh hưởng luồng dịch.
     */
    fun setBusy(busy: Boolean) {
        val intent = Intent(context, BubbleService::class.java)
            .setAction(BubbleService.ACTION_SET_BUSY)
            .putExtra(BubbleService.EXTRA_BUSY, busy)
        runCatching { context.startService(intent) }
    }

    /** Intent mở màn cài đặt quyền overlay của hệ thống. */
    fun overlayPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    private fun Context.startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        /** Nhãn notification của service (không chứa nội dung user). */
        const val NOTIFICATION_CHANNEL_ID = "bubble_service"
        const val NOTIFICATION_ID = 1001
        val CHANNEL_NAME_RES = R.string.app_name
    }
}