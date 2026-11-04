package vn.bubbledict.platform

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BE-13 — F2 bong bóng nổi (FGS `specialUse` + overlay `TYPE_APPLICATION_OVERLAY`).
 *
 * - Bong bóng tròn 56dp ở mép màn hình; kéo được, thả tự nép vào cạnh gần nhất (AC7).
 * - Vị trí lưu DataStore (`bubbleX/bubbleY`) để lần sau mở lại đúng chỗ.
 * - Tap (không kéo) -> mở màn chụp vùng [vn.bubbledict.ui.region.RegionSelectActivity] (P4).
 * - Không có quyền overlay thì service dừng ngay, không crash (E7); F1 vẫn dùng được vì độc lập.
 * - Tắt từ Settings -> [ACTION_STOP] gỡ overlay + stopSelf.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    /** View con vẽ quả bóng (gradient + elevation); window to hơn để shadow không bị cắt. */
    private var contentView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var bubbleSize: Int = 0
    private var x = 0
    private var y = 0

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var savedPositionLoaded = false

    /** BUG-15: gesture bắt đầu trong vòng tròn hay ở vành padding — quyết định cả MOVE/UP. */
    private var gestureStartedInside = false

    // AC31: trạng thái đang xử lý (vòng cung quay) — chỉ hiện khi chạy quá 0,5s, tự tắt sau 10s.
    private var ringView: View? = null
    private var ringAnimator: ValueAnimator? = null
    private var busyState = BusyState.IDLE
    private val isBusy: Boolean get() = busyState == BusyState.BUSY

    /** Màn phủ (RegionSelect) đang chiếm màn hình -> ẩn bong bóng, kể cả khi busy. */
    private var hiddenByOverlay = false

    /** AC30: cạnh + tỉ lệ gần nhất, để tính lại pixel khi xoay màn hình (không cần restart). */
    private var lastEdge = BubblePositioning.Edge.RIGHT
    private var lastRatio = -1f

    /**
     * BUG-12: toạ độ mình tính là toạ độ display, nhưng overlay có thể được xếp theo parent frame
     * (đã trừ inset status/nav/cutout) -> lệch đúng bằng inset. Hiệu chuẩn 1 lần sau khi add và
     * sau mỗi lần đổi cấu hình để bù lệch, thay vì đoán inset của từng hãng máy.
     */
    private var frameOffsetX = 0
    private var frameOffsetY = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val showBusyRunnable = Runnable { showBusyRing() }
    private val busyTimeoutRunnable = Runnable {
        Log.i(TAG, "busy timeout, clearing ring")
        setBusyState(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleSize = dp(BUBBLE_SIZE_DP)
        // Vị trí mặc định đồng bộ (§2.2): cạnh phải 4dp, cách đáy 25% chiều cao.
        x = BubblePositioning.defaultX(metrics())
        y = BubblePositioning.defaultY(metrics())
        loadSavedPosition()
        if (canDrawOverlays()) {
            addBubble()
            isRunning = true
        } else {
            // E7: thiếu quyền overlay -> dừng êm, không crash.
            Log.w(TAG, "overlay permission missing, stopping bubble")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_BUBBLE) {
            hiddenByOverlay = false
            // Hiện lại bong bóng sau màn chọn vùng, nhưng chỉ khi user vẫn bật trong Settings.
            // Nếu đã tắt (hoặc service bị kill rồi bị gọi lại), dừng êm thay vì dựng lại bong bóng.
            val container = (application as BubbleDictApp).container
            container.appScope.launch {
                val enabled = container.settingsStore.current().bubbleEnabled
                // appScope chạy Dispatchers.IO -> mọi thao tác view/window phải post về main.
                // Đọc lại ý định ẩn MỚI NHẤT ngay trên main: consent dialog làm RegionSelect
                // onStop (SHOW) rồi onStart (HIDE) liên tiếp, HIDE có thể tới sau lúc launch.
                mainHandler.post {
                    if (!hiddenByOverlay && enabled) {
                        Log.i(TAG, "bubble shown")
                        bubbleView?.visibility = View.VISIBLE
                    } else if (!enabled) {
                        Log.i(TAG, "show requested while disabled, stopping")
                        removeBubble()
                        stopSelf()
                    }
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_HIDE_BUBBLE) {
            // Designer bubble-region.md §4.1: màn chọn vùng là window riêng nên bong bóng
            // không tự ẩn -> ẩn tạm, không đổi kích thước, không reset busy.
            hiddenByOverlay = true
            Log.i(TAG, "bubble hidden by overlay")
            bubbleView?.visibility = View.INVISIBLE
            return START_STICKY
        }
        if (intent?.action == ACTION_SET_BUSY) {
            setBusyState(intent.getBooleanExtra(EXTRA_BUSY, false))
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            removeBubble()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * AC30: xoay màn hình đổi kích thước -> overlay giữ x/y tuyệt đối nên phải tính lại từ
     * cạnh + tỉ lệ đã lưu (mặc định nếu chưa từng lưu) để bong bóng vẫn nép đúng cạnh.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubbleView ?: return
        val m = metrics()
        if (lastRatio >= 0f) {
            x = BubblePositioning.xForEdge(lastEdge, m)
            y = BubblePositioning.yFromRatio(lastRatio, m)
        } else {
            x = BubblePositioning.defaultX(m)
            y = BubblePositioning.defaultY(m)
        }
        // Inset đổi theo hướng màn hình -> hiệu chuẩn lại offset frame.
        frameOffsetX = 0
        frameOffsetY = 0
        updatePosition(view, x, y)
        calibrateFrameOffset(view)
    }

    override fun onDestroy() {
        removeBubble()
        isRunning = false
        super.onDestroy()
    }

    private fun loadSavedPosition() {
        val container = (application as BubbleDictApp).container
        container.appScope.launch {
            val settings = container.settingsStore.current()
            // appScope = Dispatchers.IO -> updatePosition/visibility phải chạy trên main.
            mainHandler.post {
                if (!savedPositionLoaded) {
                    // AC30: chỉ lưu cạnh + tỉ lệ chiều cao; khôi phục theo safe area hiện tại.
                    val edge = BubblePositioning.parseEdge(settings.bubbleEdge)
                    if (edge != null && settings.bubbleYRatio >= 0f) {
                        val m = metrics()
                        lastEdge = edge
                        lastRatio = settings.bubbleYRatio
                        x = BubblePositioning.xForEdge(edge, m)
                        y = BubblePositioning.yFromRatio(settings.bubbleYRatio, m)
                        if (isRunning) bubbleView?.let {
                            updatePosition(it, x, y)
                            calibrateFrameOffset(it)
                        }
                    }
                    // Chưa từng lưu -> giữ vị trí mặc định đã tính trong onCreate.
                    savedPositionLoaded = true
                }
            }
        }
    }

    private fun savePosition() {
        val m = metrics()
        val bubbleEdge = BubblePositioning.edgeOf(x, m)
        val ratio = BubblePositioning.yToRatio(y, m)
        lastEdge = bubbleEdge
        lastRatio = ratio
        val edge = BubblePositioning.edgeName(bubbleEdge)
        val container = (application as BubbleDictApp).container
        container.appScope.launch { container.settingsStore.setBubblePosition(edge, ratio) }
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, vn.bubbledict.ui.main.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_layers)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(R.string.bubble_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                BubbleController.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(BubbleController.NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bubble_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun canDrawOverlays(): Boolean = android.provider.Settings.canDrawOverlays(this)

    private fun addBubble() {
        if (bubbleView != null) return
        val size = bubbleSize
        val pad = shadowPad()
        val windowSize = size + pad * 2

        // Quả bóng thật nằm trong content view nhỏ hơn window: shadow của elevation vẽ ra
        // ngoài biên content nên phải có padding trong window, nếu không sẽ bị cắt mất (BUG-13).
        val content = FrameLayout(this).apply {
            // Designer bubble-region.md §2.1b: nền gradient ĐẬM #4A5FA5 -> #7C5AC8 + ring trắng 35%.
            setBackgroundResource(R.drawable.bubble_bg)
            elevation = dp(BUBBLE_SHADOW_DP).toFloat()
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = false
        }
        val label = TextView(this).apply {
            // Glyph "A" trắng BOLD 28sp (§2.1b) — tương phản ~5:1 trên nền đậm.
            text = "A"
            setTextColor(Color.WHITE)
            textSize = GLYPH_SIZE_SP.toFloat()
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(label, FrameLayout.LayoutParams(size, size))

        // AC31: vòng cung quay quanh mép khi đang chụp/OCR (2dp, không đổi kích thước bong bóng).
        val ring = BusyRingView(this).apply { visibility = View.INVISIBLE }
        content.addView(ring, FrameLayout.LayoutParams(size, size))
        ringView = ring
        contentView = content

        val view = FrameLayout(this)
        view.addView(
            content,
            FrameLayout.LayoutParams(size, size, Gravity.CENTER),
        )

        val layoutParams = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // BUG-12: toạ độ x/y tính theo display, không theo parent frame đã inset.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // x/y của service là toạ độ góc trên-trái QUẢ BÓNG; window lớn hơn nên trừ padding.
            x = this@BubbleService.x - pad
            y = this@BubbleService.y - pad
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Không để hệ thống tự trừ inset (status/nav/cutout) vào vị trí overlay.
                fitInsetsTypes = 0
                setFitInsetsSides(0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params = layoutParams
        view.setOnTouchListener { v, event -> onBubbleTouch(v, event) }
        windowManager.addView(view, layoutParams)
        bubbleView = view
        calibrateFrameOffset(view)
    }

    private fun onBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // AC31: đang xử lý thì chặn tap/kéo, tránh mở thêm phiên chụp chồng.
                if (isBusy) return true
                // BUG-15: window lớn hơn bóng 10dp mỗi phía (để shadow không bị cắt). Vành padding
                // chỉ được "nuốt" chạm, KHÔNG được kích hoạt luồng chụp — nếu không, tap vào toggle
                // của app bên dưới sát bong bóng sẽ mở RegionSelect oan. Cờ phải giữ cho cả gesture
                // vì MOVE/UP của cùng lần chạm vẫn được giao tới view.
                gestureStartedInside = isInsideBubble(event.x, event.y)
                if (!gestureStartedInside) return true
                animatePress(PRESS_SCALE)
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params?.x ?: 0
                startY = params?.y ?: 0
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureStartedInside) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > 8 || abs(dy) > 8)) {
                    dragging = true
                    // UX-P1 §3.1: kéo thì bỏ press scale và nâng bóng 10 -> 14dp.
                    animatePress(1f)
                    contentView?.elevation = dp(BUBBLE_DRAG_SHADOW_DP).toFloat()
                }
                if (dragging) {
                    val m = metrics()
                    val newY = BubblePositioning.clampY((startY + dy).roundToInt(), m)
                    val newX = BubblePositioning.clampX((startX + dx).roundToInt(), m)
                    updatePosition(view, newX, newY)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureStartedInside) {
                    gestureStartedInside = false
                    return true
                }
                gestureStartedInside = false
                animatePress(1f)
                if (!dragging) {
                    onBubbleTapped()
                } else {
                    snapToEdge(view)
                    savePosition()
                }
                return true
            }
        }
        return false
    }

    private fun onBubbleTapped() {
        // Tap -> P4: xin consent MediaProjection (1 lần/phiên) rồi chọn vùng OCR.
        // Ẩn bong bóng để ảnh chụp không dính chính nó; RegionSelectActivity hiện lại khi xong.
        bubbleView?.visibility = View.INVISIBLE
        val intent = Intent(this, vn.bubbledict.ui.region.RegionSelectActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "cannot open region select: ${it.javaClass.simpleName}") }
    }

    /** AC7: thả ra tự nép về cạnh trái/phải gần nhất. */
    private fun snapToEdge(view: View) {
        // Designer chốt §2.1b: nép cạnh nhưng cách mép 4dp (0 thì bóng đổ 10dp bị cắt, trông dán phẳng).
        val m = metrics()
        val target = BubblePositioning.xForEdge(BubblePositioning.edgeOf(x, m), m)
        ValueAnimator.ofInt(x, target).apply {
            // UX-P1 §2.4: spring damping 0.82 / stiffness 380 thay tuyến tính 250ms.
            // ValueAnimator tự tôn trọng "Remove animations" (animator_duration_scale = 0).
            duration = SNAP_MS.toLong()
            interpolator = SpringInterpolator(SPRING_DAMPING, SPRING_STIFFNESS)
            addUpdateListener { animator ->
                // Cho phép overshoot rất nhỏ của spring, chỉ kẹp trong biên màn hình.
                x = (animator.animatedValue as Int).coerceIn(0, screenWidth() - bubbleSize)
                updatePosition(view, x, y)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Hạ bóng về elevation nghỉ (10dp) sau khi nép cạnh xong.
                    contentView?.elevation = dp(BUBBLE_SHADOW_DP).toFloat()
                }
            })
            start()
        }
    }

    /** BUG-15: chỉ vòng tròn bóng (56dp) nhận tap; vành padding của window trả về false. */
    private fun isInsideBubble(localX: Float, localY: Float): Boolean =
        BubblePositioning.isInsideBubble(
            localX = localX,
            localY = localY,
            windowSize = bubbleSize + shadowPad() * 2,
            bubbleSize = bubbleSize,
        )

    /** UX-P1 §3.1: scale 0.94 trong 90ms khi nhấn (không dùng khi kéo). */
    private fun animatePress(scale: Float) {
        val view = bubbleView ?: return
        view.animate().cancel()
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(PRESS_MS.toLong())
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)) // FastOutSlowIn
            .start()
    }

    private fun updatePosition(view: View, newX: Int, newY: Int) {
        x = newX
        y = newY
        val layoutParams = params ?: return
        // Bù offset parent frame (BUG-12) và padding window (BUG-13) để vị trí THỰC của quả bóng
        // trên display đúng bằng x/y mong muốn.
        layoutParams.x = newX - shadowPad() - frameOffsetX
        layoutParams.y = newY - shadowPad() - frameOffsetY
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    /**
     * BUG-12: đo vị trí thực trên display sau khi window đã attach rồi bù phần lệch do parent
     * frame (inset status/nav/cutout). Chạy 1 lần sau addView và sau mỗi lần xoay/đổi cấu hình.
     */
    private fun calibrateFrameOffset(view: View, attempt: Int = 0) {
        view.post {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            // Ý định: window phải nằm ở (x - pad, y - pad) trên display.
            val dx = loc[0] - (x - shadowPad())
            val dy = loc[1] - (y - shadowPad())
            if ((dx != 0 || dy != 0) && attempt < MAX_CALIBRATE_ATTEMPTS) {
                // Sai số có thể do (a) hệ thống bù inset, (b) lần đo rơi vào lúc window chưa kịp
                // dời -> cộng dồn rồi đo lại cho hội tụ, không "đóng băng" số offset sai.
                frameOffsetX += dx
                frameOffsetY += dy
                Log.i(TAG, "frame offset corrected dx=$frameOffsetX dy=$frameOffsetY attempt=$attempt")
                updatePosition(view, x, y)
                calibrateFrameOffset(view, attempt + 1)
            } else {
                Log.i(TAG, "frame offset dx=$frameOffsetX dy=$frameOffsetY")
            }
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun edgeMargin(): Int = dp(BUBBLE_EDGE_MARGIN_DP)

    /** Padding quanh quả bóng trong window để shadow elevation không bị cắt (BUG-13). */
    private fun shadowPad(): Int = dp(SHADOW_PAD_DP)

    private fun statusBarHeight(): Int = systemBarHeight("status_bar_height")

    private fun navBarHeight(): Int = systemBarHeight("navigation_bar_height")

    private fun systemBarHeight(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** Số đo hiện tại cho [BubblePositioning] (§2.1b: inset = status/nav bar + 4dp). */
    private fun metrics(): BubblePositioning.Metrics = BubblePositioning.Metrics(
        screenWidth = screenWidth(),
        screenHeight = screenHeight(),
        bubbleSize = bubbleSize,
        topInset = statusBarHeight() + edgeMargin(),
        bottomInset = navBarHeight() + edgeMargin(),
        edgeMargin = edgeMargin(),
    )

    /**
     * AC31: bật/tắt trạng thái đang xử lý.
     * - Hiện vòng cung sau [BUSY_SHOW_DELAY_MS] để thao tác nhanh không nháy vòng quay.
     * - Tự tắt sau [BUSY_TIMEOUT_MS] (E23: không để vòng quay chạy mãi).
     * - Busy cũng đưa bong bóng về VISIBLE (nó bị ẩn trong lúc chụp frame để không dính ảnh).
     */
    private fun setBusyState(busy: Boolean) {
        busyState = if (busy) BusyState.BUSY else BusyState.IDLE
        mainHandler.removeCallbacks(showBusyRunnable)
        mainHandler.removeCallbacks(busyTimeoutRunnable)
        if (!busy) {
            hideBusyRing()
            return
        }
        if (!hiddenByOverlay) bubbleView?.visibility = View.VISIBLE
        mainHandler.postDelayed(showBusyRunnable, BUSY_SHOW_DELAY_MS.toLong())
        mainHandler.postDelayed(busyTimeoutRunnable, BUSY_TIMEOUT_MS.toLong())
    }

    private fun showBusyRing() {
        val ring = ringView ?: return
        if (!isBusy) return
        // Designer §2.2 / duyệt AC31(b): tắt inner ring tĩnh khi busy, chỉ còn 1 vòng đang quay.
        contentView?.setBackgroundResource(R.drawable.bubble_bg_busy)
        ring.visibility = View.VISIBLE
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = RING_TURN_MS.toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { ring.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun hideBusyRing() {
        ringAnimator?.cancel()
        ringAnimator = null
        ringView?.let {
            it.rotation = 0f
            it.visibility = View.INVISIBLE
        }
        contentView?.setBackgroundResource(R.drawable.bubble_bg)
    }

    private fun removeBubble() {
        mainHandler.removeCallbacks(showBusyRunnable)
        mainHandler.removeCallbacks(busyTimeoutRunnable)
        busyState = BusyState.IDLE
        hideBusyRing()
        ringView = null
        contentView = null
        bubbleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        bubbleView = null
        params = null
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /** AC31/BE-24: state machine tối giản — Done/Failed chỉ là quay về [IDLE]. */
    private enum class BusyState { IDLE, BUSY }

    companion object {
        const val TAG = "BubbleService"
        const val ACTION_START = "vn.bubbledict.action.START_BUBBLE"
        const val ACTION_SHOW_BUBBLE = "vn.bubbledict.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "vn.bubbledict.action.HIDE_BUBBLE"
        const val ACTION_STOP = "vn.bubbledict.action.STOP_BUBBLE"
        const val ACTION_SET_BUSY = "vn.bubbledict.action.SET_BUSY"
        const val EXTRA_BUSY = "vn.bubbledict.extra.BUSY"
        const val CHANNEL_ID = BubbleController.NOTIFICATION_CHANNEL_ID

        // bubble-region.md §2.1b — nguồn sự thật cho visual bong bóng; đổi số thì sửa cả doc.
        private const val BUBBLE_SIZE_DP = 56
        private const val BUBBLE_EDGE_MARGIN_DP = 4
        private const val BUBBLE_SHADOW_DP = 10
        private const val GLYPH_SIZE_SP = 28
        private const val SNAP_MS = 250

        // UX-P1 (visual-polish-v2 §2.4/§3.1): press scale + nâng bóng khi kéo.
        private const val PRESS_MS = 90
        private const val PRESS_SCALE = 0.94f
        private const val BUBBLE_DRAG_SHADOW_DP = 14
        /**
         * Designer chốt phương án (1), bản 10dp: window 76dp = bong bóng 56dp + 10dp mỗi phía,
         * đủ cho shadow idle 10dp vẽ trọn. Khi kéo elevation 14dp vẫn cắt 4dp — chấp nhận được vì
         * ngón tay đang che bong bóng. Vành chết 10dp (~2,5% bề ngang màn hình) ăn touch của app dưới.
         */
        private const val SHADOW_PAD_DP = 10
        /** Số vòng hiệu chuẩn tối đa (đo lại sau mỗi lần dời window) để tránh lặp vô hạn. */
        private const val MAX_CALIBRATE_ATTEMPTS = 3
        private const val SPRING_DAMPING = 0.82f
        private const val SPRING_STIFFNESS = 380f

        // AC31 — trạng thái đang xử lý.
        private const val BUSY_SHOW_DELAY_MS = 500
        private const val BUSY_TIMEOUT_MS = 10_000
        private const val RING_TURN_MS = 900

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

/** AC31: vòng cung 2dp quay quanh mép bong bóng khi đang chụp/OCR. */
private class BusyRingView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    /** Track (đường ray) trắng 22% full vòng — cho biết đây là vòng tiến trình, không phải highlight mép. */
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x38FFFFFF
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }

    /** Cung trắng 3dp đang quay — đậm hơn track để thấy chuyển động. */
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        // Lùi bán kính vào 2dp so với bản trước (Designer duyệt arc v2: không để cung trượt
        // ra ngoài mép bong bóng thành vệt lưỡi liềm); stroke giữ 3dp.
        val inset = arcPaint.strokeWidth / 2f + 4f * density
        arcRect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
        canvas.drawArc(arcRect, -90f, 90f, false, arcPaint)
    }
}

/**
 * UX-P1 §2.4 — spring cho nép cạnh: dampingRatio 0.82, stiffness 380.
 * Nghiệm của dao động tắt dần bậc 2 (m = 1) nên không cần thêm dependency dynamicanimation.
 */
private class SpringInterpolator(
    private val dampingRatio: Float,
    private val stiffness: Float,
) : Interpolator {

    private val omegaN = sqrt(stiffness)
    private val omegaD = omegaN * sqrt((1f - dampingRatio * dampingRatio).coerceAtLeast(0.0001f))
    private val decay = dampingRatio * omegaN

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (omegaD <= 0f) return 1f
        val t = input * (SNAP_SECONDS)
        val envelope = exp(-decay * t)
        return 1f - envelope * (cos(omegaD * t) + (decay / omegaD) * sin(omegaD * t))
    }

    private companion object {
        /** Thời gian spring settle, khớp giá trị 250ms của bảng hằng số §2.1b. */
        const val SNAP_SECONDS = 0.25f
    }
}
