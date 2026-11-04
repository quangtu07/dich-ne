package vn.bubbledict.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Phiên chụp màn hình dùng chung (BE-14):
 * giữ 1 MediaProjection/VirtualDisplay trong [ScreenCaptureService] và phát frame gần nhất
 * cho UI đang chờ. Không giữ ảnh trong DB; ảnh crop chỉ ở bộ nhớ/cache tạm.
 */
object ScreenCaptureSession {

    interface Listener {
        fun onSessionReady()
        fun onFrame(bitmap: Bitmap)
        fun onFailed(code: String)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var latestFrame: Bitmap? = null
        private set

    @Volatile
    private var ready = false

    fun isSessionActive(): Boolean = ready

    fun addListener(listener: Listener) {
        listeners += listener
        if (ready) listener.onSessionReady()
        latestFrame?.let { listener.onFrame(it) }
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** Intent xin consent MediaProjection (1 lần/phiên). */
    fun consentIntent(context: Context): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14+ mặc định cho chọn "một app"; ta cần cả màn hình để OCR vùng bất kỳ.
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
    }

    /** Activity gọi sau khi user đồng ý: truyền kết quả sang service giữ phiên. */
    fun startSession(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_START)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopSession(context: Context) {
        latestFrame = null
        ready = false
        context.startService(
            Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP),
        )
    }

    /** Yêu cầu frame mới nhất; service liên tục cập nhật từ VirtualDisplay. */
    fun latestFrameOrNull(): Bitmap? = latestFrame

    internal fun onSessionStarted() {
        ready = true
        mainHandler.post { listeners.toList().forEach { it.onSessionReady() } }
    }

    internal fun onFrame(bitmap: Bitmap) {
        latestFrame = bitmap
        mainHandler.post { listeners.toList().forEach { it.onFrame(bitmap) } }
    }

    internal fun onSessionFailed(code: String) {
        ready = false
        mainHandler.post { listeners.toList().forEach { it.onFailed(code) } }
    }

    internal fun onSessionEnded() {
        ready = false
        latestFrame = null
    }
}