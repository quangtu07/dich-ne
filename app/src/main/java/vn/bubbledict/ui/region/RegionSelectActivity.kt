package vn.bubbledict.ui.region

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.R
import vn.bubbledict.data.offline.OcrEngine
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.platform.ScreenCaptureSession
import vn.bubbledict.ui.result.ResultActivityContract
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * UX-05 §4 (FE-08) — màn chọn vùng OCR.
 *
 * Full-screen đen, ảnh chụp Fit (không crop), scrim 45% ngoài vùng chọn, viền 2dp + 4 ngoặc góc
 * + 4 chấm tay nắm; vùng mặc định 80%×15% căn giữa; kéo trong = di chuyển, kéo góc/viền = resize,
 * kéo ngoài = vẽ vùng mới; tối thiểu 40×24dp; 3 nút Huỷ / Chụp lại / Dịch.
 *
 * Toạ độ gửi đi là **tỉ lệ 0..1 của ảnh gốc** (BE crop theo bitmap), không phải pixel màn hình.
 */
class RegionSelectActivity : ComponentActivity() {

    private companion object {
        /**
         * Đợi dialog consent + hiệu ứng dim tan hẳn rồi mới nhận frame (ms).
         * 700ms từng để dính lớp trắng mờ của animation dialog -> nâng lên 1200ms.
         */
        const val FRAME_SETTLE_MS = 1200L
    }

    private var frame by mutableStateOf<Bitmap?>(null)
    private var darkFrame by mutableStateOf(false)
    private var ocrEmpty by mutableStateOf(false)
    private var ocrFailed by mutableStateOf(false)
    private var ocrTimeout by mutableStateOf(false)
    private var captured = false
    private var permissionDenied by mutableStateOf(false)
    private var permissionSecurityError by mutableStateOf(false)

    /** AC31 (§4.3b): OCR đang chạy — card progress chỉ hiện sau 0,5s để không nhấp nháy. */
    private var processing by mutableStateOf(false)
    private var progressVisible by mutableStateOf(false)

    /**
     * Frame đầu sau khi consent còn chứa chính dialog MediaProjection (ảnh đóng băng bị "dính" dialog).
     * Chỉ nhận frame sau mốc này — lúc đó activity vẫn trong suốt, ảnh chụp là app bên dưới.
     */
    private var acceptFramesAt = 0L

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                acceptFramesAt = SystemClock.elapsedRealtime() + FRAME_SETTLE_MS
                // SecurityException (token hết hạn/chặn) coi như huỷ -> hướng dẫn, không crash.
                val started = runCatching {
                    ScreenCaptureSession.startSession(this, result.resultCode, data)
                }.isSuccess
                if (!started) {
                    permissionSecurityError = true
                    permissionDenied = true
                }
            } else {
                // AC9: huỷ consent -> màn hướng dẫn cấp lại quyền (không crash, không treo màn trống).
                permissionDenied = true
            }
        }

    private val listener = object : ScreenCaptureSession.Listener {
        override fun onSessionReady() = Unit

        override fun onFrame(bitmap: Bitmap) {
            // Chỉ lấy frame ĐẦU khi màn hình mình còn trống: tránh feedback loop và giữ ảnh đứng yên.
            if (captured) return
            if (SystemClock.elapsedRealtime() < acceptFramesAt) return
            captured = true
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            frame = copy
            darkFrame = isMostlyDark(copy)
            ocrEmpty = false
            ocrFailed = false
            ocrTimeout = false
        }

        override fun onFailed(code: String) {
            Toast.makeText(this@RegionSelectActivity, R.string.region_error_capture, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenCaptureSession.addListener(listener)
        val existing = ScreenCaptureSession.latestFrameOrNull()
        if (existing != null) {
            val copy = existing.copy(Bitmap.Config.ARGB_8888, false)
            frame = copy
            darkFrame = isMostlyDark(copy)
            captured = true
        } else if (!ScreenCaptureSession.isSessionActive()) {
            // 1 consent/phiên: service đã sống thì dùng lại, chưa có mới xin.
            consentLauncher.launch(ScreenCaptureSession.consentIntent(this))
        }
        setContent {
            BubbleDictTheme {
                // Trước khi có frame: nền trong suốt để capture không chụp lại UI của chính app.
                val currentFrame = frame
                if (permissionDenied) {
                    PermissionBlockedScreen(
                        securityError = permissionSecurityError,
                        onClose = { finish() },
                        onRetry = {
                            permissionDenied = false
                            permissionSecurityError = false
                            consentLauncher.launch(ScreenCaptureSession.consentIntent(this@RegionSelectActivity))
                        },
                    )
                } else if (currentFrame == null) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    RegionSelectScreen(
                        frame = currentFrame,
                        darkFrame = darkFrame,
                        ocrEmpty = ocrEmpty,
                        ocrFailed = ocrFailed,
                        ocrTimeout = ocrTimeout,
                        onConfirm = { rect -> confirmSelection(rect) },
                        onRetake = ::retake,
                        onCancel = { finish() },
                        processing = processing,
                        progressVisible = progressVisible,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Designer §4.1: bong bóng là window riêng, phải ẩn khi màn chọn vùng hiện — nếu không
        // nó đè lên các nút Huỷ/Chụp lại/Dịch (và cả khi busy ring được bật).
        runCatching { (application as BubbleDictApp).container.bubbleController.setVisible(false) }
    }

    // Cố ý KHÔNG hiện lại ở onStop: system consent dialog là activity khác nên onStop chạy
    // -> bong bóng lóe lên đè màn chọn vùng (Designer bắt được). Chỉ hiện lại khi huỷ hẳn activity.
    override fun onDestroy() {
        ScreenCaptureSession.removeListener(listener)
        runCatching { (application as BubbleDictApp).container.bubbleController.setVisible(true) }
        // Hiện lại bong bóng sau khi chọn xong/huỷ.
        // Bọc runCatching: startService từ background có thể ném ForegroundServiceStartNotAllowedException
        // (Android 12+) — không để crash trong onDestroy. BubbleService tự kiểm `bubbleEnabled` (BE).
        runCatching {
            startService(
                Intent(this, vn.bubbledict.platform.BubbleService::class.java)
                    .setAction(vn.bubbledict.platform.BubbleService.ACTION_SHOW_BUBBLE),
            )
        }
        super.onDestroy()
    }

    /** Chụp lại: ẩn UI (frame = null -> Box trong suốt) rồi mới lấy frame kế tiếp, tránh tự chụp UI. */
    private fun retake() {
        captured = false
        acceptFramesAt = SystemClock.elapsedRealtime() + FRAME_SETTLE_MS
        frame = null
        darkFrame = false
        ocrEmpty = false
        ocrFailed = false
        if (!ScreenCaptureSession.isSessionActive()) {
            consentLauncher.launch(ScreenCaptureSession.consentIntent(this))
        }
    }

    /** @param rect tỉ lệ 0..1 của ảnh gốc (không phải pixel màn hình). */
    private fun confirmSelection(rect: Rect) {
        val bitmap = frame ?: return
        if (darkFrame) return
        val cropped = cropBitmap(bitmap, rect) ?: run {
            Toast.makeText(this, R.string.region_error_capture, Toast.LENGTH_LONG).show()
            return
        }
        val container = (application as BubbleDictApp).container
        val ocr = container.ocrEngine
        processing = true
        progressVisible = false
        lifecycleScope.launch {
            // AC31: chỉ hiện card progress nếu OCR chậm hơn 0,5s.
            val slowJob = launch {
                delay(500)
                if (processing) progressVisible = true
            }
            // AC31/E23: dùng một state busy của bong bóng (vòng cung sau 0,5s, chặn tap) —
            // bật sau khi đã crop để ảnh không dính bong bóng.
            container.bubbleController.setBusy(true)
            try {
                when (val result = ocr.recognize(cropped)) {
                    is OcrEngine.OcrResult.Text -> {
                        startActivity(
                            ResultActivityContract.newIntent(
                                this@RegionSelectActivity,
                                result.text,
                                TextSource.OCR,
                            ),
                        )
                        finish()
                    }

                    OcrEngine.OcrResult.Empty -> ocrEmpty = true

                    is OcrEngine.OcrResult.Error -> {
                        // E23: timeout 10s / OCR init lỗi -> báo rõ, giữ nguyên vùng chọn để chọn lại.
                        if (result.code == "E23") ocrTimeout = true else ocrFailed = true
                    }
                }
            } finally {
                slowJob.cancel()
                processing = false
                progressVisible = false
                container.bubbleController.setBusy(false)
                if (cropped !== bitmap) cropped.recycle()
            }
        }
    }

    /** rect là tỉ lệ 0..1 của ảnh gốc. */
    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? = runCatching {
        val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }.getOrNull()

    /** E9/AC10: ảnh toàn đen (app chặn chụp) — lấy mẫu lưới 8×8, ngưỡng sáng rất thấp. */
    private fun isMostlyDark(bitmap: Bitmap): Boolean {
        var sum = 0L
        var samples = 0
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (r * 299 + g * 587 + b * 114) / 1000
                samples++
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) return false
        return sum / samples < 6
    }
}

/** AC9 §4.3c — huỷ consent / SecurityException: màn trạng thái rỗng, KHÔNG hiện thanh 3 nút. */
@Composable
private fun PermissionBlockedScreen(
    securityError: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(Color(0xFF161619).copy(alpha = 0.92f)),
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.region_blocked_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(
                    if (securityError) {
                        R.string.region_blocked_body_security
                    } else {
                        R.string.region_blocked_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.region_blocked_retry))
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.region_blocked_close), color = Color.White)
                }
            }
        }
    }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.45f)

@Composable
private fun RegionSelectScreen(
    frame: Bitmap,
    darkFrame: Boolean,
    ocrEmpty: Boolean,
    ocrFailed: Boolean,
    ocrTimeout: Boolean,
    onConfirm: (Rect) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    processing: Boolean,
    progressVisible: Boolean,
) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    // Card progress (AC31) có thể bị huỷ bằng back = đóng màn chọn vùng.
    BackHandler(enabled = processing) { onCancel() }
    var showHint by remember { mutableStateOf(true) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var selection by remember { mutableStateOf<Rect?>(null) }
    var hintTick by remember { mutableStateOf(0) }

    val minWidthPx = with(density) { 40.dp.toPx() }
    val minHeightPx = with(density) { 24.dp.toPx() }

    // Hint tự ẩn sau 3s; chạm lần đầu hiện lại.
    LaunchedEffect(hintTick) {
        showHint = true
        delay(3000)
        showHint = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()

        // Ảnh Fit: tính khung hiển thị thật để quy đổi toạ độ (tránh crop lệch do letterbox).
        val fit = remember(frame, canvasW, canvasH) {
            val scale = minOf(canvasW / frame.width, canvasH / frame.height)
            val w = frame.width * scale
            val h = frame.height * scale
            Rect(
                left = (canvasW - w) / 2f,
                top = (canvasH - h) / 2f,
                right = (canvasW + w) / 2f,
                bottom = (canvasH + h) / 2f,
            )
        }
        LaunchedEffect(fit) {
            imageRect = fit
            // Vùng mặc định UX-05 §4.2: 80% ngang, 15% cao, căn giữa.
            val w = fit.width * 0.8f
            val h = fit.height * 0.15f
            val cx = fit.center.x
            val cy = fit.center.y
            selection = Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        }

        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Vùng chọn + scrim + viền + ngoặc góc + tay nắm.
        val selected = selection
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "selection" }
                // KHÔNG key theo `selected`: cứ mỗi lần selection đổi, pointerInput bị restart và
                // gesture kéo bị huỷ giữa chừng (bug thấy thật: chỉ nhích được vài px).
                .pointerInput(fit) {
                    var mode: DragMode = DragMode.None
                    var corner: Corner? = null
                    var startRect = Rect.Zero
                    var startPoint = Offset.Zero

                    detectDragGestures(
                        onDragStart = { point ->
                            if (processing) return@detectDragGestures
                            hintTick++
                            val sel = selection ?: return@detectDragGestures
                            startRect = sel
                            startPoint = point
                            val cornerR = with(density) { 24.dp.toPx() }
                            corner = nearestCorner(point, sel, cornerR)
                            val edgeTolerance = with(density) { 12.dp.toPx() }
                            mode = when {
                                corner != null -> DragMode.Resize
                                point.x in (sel.left - edgeTolerance)..(sel.right + edgeTolerance) &&
                                    point.y in (sel.top - edgeTolerance)..(sel.bottom + edgeTolerance) ->
                                    DragMode.Move

                                else -> {
                                    mode = DragMode.Draw
                                    selection = Rect(point, point)
                                    startRect = Rect(point, point)
                                    DragMode.Draw
                                }
                            }
                        },
                        onDragCancel = { mode = DragMode.None },
                        onDragEnd = { mode = DragMode.None },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val sel = selection ?: return@detectDragGestures
                            when (mode) {
                                DragMode.Move -> {
                                    val dx = change.position.x - startPoint.x
                                    val dy = change.position.y - startPoint.y
                                    selection = clampToImage(
                                        shift(startRect, dx, dy),
                                        fit,
                                        minWidthPx,
                                        minHeightPx,
                                    )
                                }

                                DragMode.Resize -> {
                                    selection = resize(
                                        startRect,
                                        corner ?: Corner.TopLeft,
                                        change.position,
                                        fit,
                                        minWidthPx,
                                        minHeightPx,
                                    )
                                }

                                DragMode.Draw -> {
                                    selection = normalizeRect(startRect.topLeft, change.position)
                                        .let { clampToImage(it, fit, minWidthPx, minHeightPx) }
                                }

                                DragMode.None -> Unit
                            }
                        },
                    )
                },
        ) {
            val sel = selection ?: return@Canvas
            // Scrim 4 dải quanh vùng chọn (vùng chọn giữ độ sáng gốc).
            drawRect(SCRIM, Offset.Zero, Size(size.width, sel.top))
            drawRect(SCRIM, Offset(0f, sel.bottom), Size(size.width, size.height - sel.bottom))
            drawRect(SCRIM, Offset(0f, sel.top), Size(sel.left, sel.height))
            drawRect(SCRIM, Offset(sel.right, sel.top), Size(size.width - sel.right, sel.height))

            // Viền 2dp primary.
            drawRect(primary, sel.topLeft, sel.size, style = Stroke(width = 2.dp.toPx()))
            // 4 ngoặc góc 20dp dày 3dp.
            drawCorners(sel, primary, length = 20.dp.toPx(), width = 3.dp.toPx())
            // 4 chấm tay nắm 8dp.
            val dot = 4.dp.toPx()
            listOf(sel.topLeft, sel.topRight, sel.bottomLeft, sel.bottomRight).forEach { c ->
                drawCircle(primary, radius = dot, center = c)
            }
        }

        // Banner lỗi (E9/E10/E12) trên ảnh.
        val banner: Pair<Int, Int>? = when {
            darkFrame -> R.string.region_dark_title to R.string.region_dark_body
            ocrTimeout -> R.string.region_timeout_title to R.string.region_timeout_body
            ocrEmpty -> R.string.region_empty_title to R.string.region_empty_body
            ocrFailed -> R.string.region_retry_title to R.string.region_retry_body
            else -> null
        }
        banner?.let { (titleRes, bodyRes) ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (darkFrame) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.region_close))
                    }
                }
            }
        }

        // Hint trên cùng, tự ẩn 3s, hiện lại khi chạm.
        if (banner == null) {
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.region_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Thanh điều hướng đáy: Huỷ / Chụp lại / Dịch.
        // Designer §4.3: dải gradient đen 0% -> 70% cao 120dp để chữ trắng không đè lên nội dung sáng.
        val tooSmall = selected == null ||
            selected.width < minWidthPx || selected.height < minHeightPx
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    ),
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
            TextButton(
                onClick = onCancel,
                enabled = !processing,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                ),
            ) {
                Text(stringResource(R.string.region_cancel))
            }
            TextButton(
                onClick = onRetake,
                enabled = !processing,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                ),
            ) {
                Text(stringResource(R.string.region_retake))
            }
                Column(horizontalAlignment = Alignment.End) {
                    if (tooSmall) {
                        Text(
                            text = stringResource(R.string.region_too_small),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Button(
                        onClick = {
                            val sel = selection ?: return@Button
                            onConfirm(toImageFraction(sel, fit))
                        },
                        enabled = !tooSmall && !darkFrame && !processing,
                        // Designer nit: tone đậm hơn của cùng màu brand để nổi trên thanh đen.
                        // Khi busy: mờ 38% (giữ hình nút), KHÔNG để nút biến mất.
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.18f),
                            disabledContainerColor = lerp(
                                MaterialTheme.colorScheme.primary,
                                Color.Black,
                                0.18f,
                            ).copy(alpha = 0.38f),
                            disabledContentColor = Color.White.copy(alpha = 0.9f),
                        ),
                    ) {
                        Text(stringResource(R.string.region_translate))
                    }
                }
            }
        }

        // AC31 §4.3b: OCR chậm >0,5s -> scrim 20% + card spinner (fade 120ms), không nhận thao tác.
        AnimatedVisibility(
            visible = progressVisible,
            enter = fadeIn(tween(120)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
                    .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    // surfaceContainerHigh theo Design System, bóng 3dp (tonalElevation).
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier.widthIn(max = 240.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            text = stringResource(R.string.region_processing),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private enum class DragMode { None, Move, Resize, Draw }

private enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

private fun nearestCorner(point: Offset, rect: Rect, radius: Float): Corner? {
    val corners = listOf(
        Corner.TopLeft to rect.topLeft,
        Corner.TopRight to rect.topRight,
        Corner.BottomLeft to rect.bottomLeft,
        Corner.BottomRight to rect.bottomRight,
    )
    val nearest = corners.minByOrNull { (_, c) -> (c - point).getDistance() } ?: return null
    return if ((nearest.second - point).getDistance() <= radius) nearest.first else null
}

private fun normalizeRect(a: Offset, b: Offset): Rect = Rect(
    left = minOf(a.x, b.x),
    top = minOf(a.y, b.y),
    right = maxOf(a.x, b.x),
    bottom = maxOf(a.y, b.y),
)

private fun shift(rect: Rect, dx: Float, dy: Float): Rect =
    Rect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)

private fun clampToImage(rect: Rect, image: Rect, minW: Float, minH: Float): Rect {
    var left = rect.left.coerceIn(image.left, image.right)
    var top = rect.top.coerceIn(image.top, image.bottom)
    var right = rect.right.coerceIn(image.left, image.right)
    var bottom = rect.bottom.coerceIn(image.top, image.bottom)
    if (right - left < minW) {
        right = (left + minW).coerceAtMost(image.right)
        left = (right - minW).coerceAtLeast(image.left)
    }
    if (bottom - top < minH) {
        bottom = (top + minH).coerceAtMost(image.bottom)
        top = (bottom - minH).coerceAtLeast(image.top)
    }
    return Rect(left, top, right, bottom)
}

/** Resize bằng góc đang kéo; góc đối diện giữ nguyên, kẹp biên ảnh + kích thước tối thiểu. */
private fun resize(start: Rect, corner: Corner, point: Offset, image: Rect, minW: Float, minH: Float): Rect {
    val x = point.x.coerceIn(image.left, image.right)
    val y = point.y.coerceIn(image.top, image.bottom)
    val raw = when (corner) {
        Corner.TopLeft -> Rect(x, y, start.right, start.bottom)
        Corner.TopRight -> Rect(start.left, y, x, start.bottom)
        Corner.BottomLeft -> Rect(x, start.top, start.right, y)
        Corner.BottomRight -> Rect(start.left, start.top, x, y)
    }
    return clampToImage(normalizeRect(raw.topLeft, raw.bottomRight), image, minW, minH)
}

/** Đổi rect hiển thị -> tỉ lệ 0..1 của ảnh gốc (BE crop theo pixel ảnh). */
private fun toImageFraction(rect: Rect, image: Rect): Rect {
    val w = image.width.coerceAtLeast(1f)
    val h = image.height.coerceAtLeast(1f)
    return Rect(
        left = ((rect.left - image.left) / w).coerceIn(0f, 1f),
        top = ((rect.top - image.top) / h).coerceIn(0f, 1f),
        right = ((rect.right - image.left) / w).coerceIn(0f, 1f),
        bottom = ((rect.bottom - image.top) / h).coerceIn(0f, 1f),
    )
}

/** Vẽ 4 ngoặc góc chữ L. */
private fun DrawScope.drawCorners(rect: Rect, color: Color, length: Float, width: Float) {
    val stroke = Stroke(width = width)
    val l = length.coerceAtMost(rect.width / 2f)
    val t = length.coerceAtMost(rect.height / 2f)
    // top-left
    drawPath(Path().apply { moveTo(rect.left, rect.top + t); lineTo(rect.left, rect.top); lineTo(rect.left + l, rect.top) }, color, style = stroke)
    // top-right
    drawPath(Path().apply { moveTo(rect.right - l, rect.top); lineTo(rect.right, rect.top); lineTo(rect.right, rect.top + t) }, color, style = stroke)
    // bottom-left
    drawPath(Path().apply { moveTo(rect.left, rect.bottom - t); lineTo(rect.left, rect.bottom); lineTo(rect.left + l, rect.bottom) }, color, style = stroke)
    // bottom-right
    drawPath(Path().apply { moveTo(rect.right - l, rect.bottom); lineTo(rect.right, rect.bottom); lineTo(rect.right, rect.bottom - t) }, color, style = stroke)
}
