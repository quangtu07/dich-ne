package vn.bubbledict.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.ui.theme.BubbleDictTheme
import java.time.LocalDate

/**
 * Hook debug cho QA — **chỉ có trong debug build** (source set `src/debug/`), file manifest
 * riêng `src/debug/AndroidManifest.xml` khai báo `exported=false`. Release không chứa class này.
 *
 * Mục đích: giả lập hết quota MyMemory để QA chấm AC25/E19 mà không phải chờ hết 5.000 ký tự/ngày,
 * và bật/tắt công tắc nguồn online để kiểm AC26 (0 request).
 */
class DebugHooksActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BubbleDictTheme {
                HooksScreen(
                    onBlockQuota = {
                        lifecycleScope.launch {
                            val container = (application as BubbleDictApp).container
                            container.settingsStore.setKeylessQuotaBlockedOn(LocalDate.now().toString())
                            toast("Đã giả lập hết quota hôm nay (E19)")
                        }
                    },
                    onClearQuota = {
                        lifecycleScope.launch {
                            val container = (application as BubbleDictApp).container
                            container.settingsStore.setKeylessQuotaBlockedOn("")
                            toast("Đã xoá cờ hết quota")
                        }
                    },
                    onToggleOnline = { enabled ->
                        lifecycleScope.launch {
                            val container = (application as BubbleDictApp).container
                            container.settingsStore.setOnlineSourcesEnabled(enabled)
                            toast(if (enabled) "Đã bật nguồn online" else "Đã tắt nguồn online (AC26)")
                        }
                    },
                    onClearCache = {
                        lifecycleScope.launch {
                            val container = (application as BubbleDictApp).container
                            container.maintenance.clearCache()
                            toast("Đã xoá cache")
                        }
                    },
                    onOpenRegionSelect = {
                        // exported=false nên QA không `am start` được; hook này thay thế.
                        startActivity(
                            android.content.Intent(
                                this@DebugHooksActivity,
                                vn.bubbledict.ui.region.RegionSelectActivity::class.java,
                            ),
                        )
                    },
                    onOcrDelay = { ms ->
                        vn.bubbledict.data.offline.OcrEngine.debugArtificialDelayMs = ms
                        toast(
                            when {
                                ms <= 0 -> "Đã tắt giả lập OCR chậm"
                                ms > 10_000 -> "OCR trễ ${ms / 1000}s — sẽ ra E23 (timeout 10s)"
                                else -> "OCR trễ ${ms / 1000}s — chụp card AC31(a)"
                            },
                        )
                    },
                    onBusy = { busy ->
                        // AC31: QA chụp ảnh vòng cung đang xử lý mà không phải chờ OCR thật.
                        val container = (application as BubbleDictApp).container
                        container.bubbleController.setBusy(busy)
                        toast(
                            if (busy) "Busy: vòng cung hiện sau 0,5s (cần bong bóng đang bật)"
                            else "Đã tắt busy",
                        )
                    },
                )
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun HooksScreen(
    onBlockQuota: () -> Unit,
    onClearQuota: () -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onBusy: (Boolean) -> Unit,
    onOcrDelay: (Long) -> Unit,
    onOpenRegionSelect: () -> Unit,
) {
    // Đọc lại từ knob để warning không mất khi xoay/onCreate lại activity.
    var ocrDelayMs by remember { mutableStateOf(vn.bubbledict.data.offline.OcrEngine.debugArtificialDelayMs) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug hooks (chỉ debug build)", style = MaterialTheme.typography.titleMedium)
            if (ocrDelayMs > 0L) {
                // Designer yêu cầu: cảnh báo thường trực để QA không quên tắt rồi chấm nhầm AC8/AC9.
                Text(
                    text = if (ocrDelayMs > 10_000L) {
                        "ĐANG GIẢ LẬP OCR TREO ${ocrDelayMs / 1000}s (ra E23) — NHỚ TẮT"
                    } else {
                        "ĐANG GIẢ LẬP OCR CHẬM ${ocrDelayMs / 1000}s — NHỚ TẮT"
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Button(onClick = onBlockQuota) { Text("Giả lập hết quota MyMemory (E19)") }
            Button(onClick = onClearQuota) { Text("Xoá cờ hết quota") }
            Button(onClick = { onToggleOnline(true) }) { Text("Bật nguồn online") }
            Button(onClick = { onToggleOnline(false) }) { Text("Tắt nguồn online (AC26)") }
            Button(onClick = onClearCache) { Text("Xoá cache") }
            Button(onClick = onOpenRegionSelect) {
                Text("Mở màn chọn vùng (AC9 / AC31a)")
            }
            Button(onClick = { ocrDelayMs = 2_000L; onOcrDelay(2_000L) }) {
                Text("Giả lập OCR chậm 2s (card AC31a)")
            }
            Button(onClick = { ocrDelayMs = 12_000L; onOcrDelay(12_000L) }) {
                Text("Giả lập OCR treo 12s (E23)")
            }
            Button(onClick = { ocrDelayMs = 0L; onOcrDelay(0L) }) {
                Text("Tắt giả lập OCR chậm")
            }
            Button(onClick = { onBusy(true) }) { Text("Busy bong bóng — vòng cung AC31") }
            Button(onClick = { onBusy(false) }) { Text("Tắt busy bong bóng") }
        }
    }
}