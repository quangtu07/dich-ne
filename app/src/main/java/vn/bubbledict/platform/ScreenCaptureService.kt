package vn.bubbledict.platform

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import vn.bubbledict.R

/**
 * BE-14 — giữ **một** MediaProjection + VirtualDisplay + ImageReader sống cả phiên (chốt Chief).
 *
 * - Consent xin 1 lần/phiên ở `RegionSelectActivity`; service nhận kết quả qua Intent.
 * - `MediaProjection.Callback` đăng ký **trước** `createVirtualDisplay` (bắt buộc API 34+).
 * - Frame gần nhất đọc được từ singleton [ScreenCaptureSession]; service bị kill/khoá màn hình
 *   thì phiên coi như hết -> lần chụp sau xin consent lại.
 * - Service chạy FGS `mediaProjection` để hợp lệ trên Android 14+.
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseAll()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultCode == Activity.RESULT_OK && data != null) {
                startSession(resultCode, data)
            } else {
                Log.w(TAG, "missing projection consent")
                ScreenCaptureSession.onSessionFailed(E7_NO_CONSENT)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startSession(resultCode: Int, data: Intent) {
        if (projection != null) return // 1 phiên: dùng lại display đang sống.
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, data) ?: run {
            ScreenCaptureSession.onSessionFailed(E7_NO_CONSENT)
            return
        }
        handlerThread = HandlerThread("screen-capture").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        // Callback phải đăng ký TRƯỚC createVirtualDisplay (API 34+).
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "projection stopped by system")
                    ScreenCaptureSession.onSessionEnded()
                    releaseAll()
                }
            },
            handler,
        )

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / plane.pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (cropped !== bitmap) bitmap.recycle()
                ScreenCaptureSession.onFrame(cropped)
            } catch (e: Exception) {
                Log.w(TAG, "frame read failed: ${e.javaClass.simpleName}")
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "bubble-dict-capture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        projection = mediaProjection
        imageReader = reader
        ScreenCaptureSession.onSessionStarted()
    }

    private fun releaseAll() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        projection = null
        virtualDisplay = null
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    override fun onDestroy() {
        releaseAll()
        ScreenCaptureSession.onSessionEnded()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screenshot_monitor)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val TAG = "ScreenCaptureService"
        const val ACTION_START = "vn.bubbledict.action.START_CAPTURE"
        const val ACTION_STOP = "vn.bubbledict.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "screen_capture"
        const val E7_NO_CONSENT = "E7"
    }
}