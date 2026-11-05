package vn.bubbledict.ui.debug

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import vn.bubbledict.R
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * Màn dev: kiểm tra TTS voice tiếng Anh trên máy đang chạy (trả lời câu hỏi mở của Designer
 * và chuẩn bị dữ liệu cho BE-12/QA AC18–AC22). Không phải UI của sản phẩm.
 *
 *   adb shell am start -n vn.bubbledict/.ui.debug.TtsProbeActivity
 */
class TtsProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BubbleDictTheme {
                val context = LocalContext.current
                var lines by remember { mutableStateOf(listOf("Đang khởi tạo TextToSpeech…")) }

                DisposableEffect(Unit) {
                    var engine: TextToSpeech? = null
                    engine = TextToSpeech(context, { status ->
                        val out = mutableListOf<String>()
                        out += "init status = $status (0 = SUCCESS)"
                        val tts = engine
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            out += "defaultEngine = ${tts.defaultEngine ?: "(null)"}"
                            out += "voices.size = ${tts.voices?.size ?: 0}"
                            for ((label, locale) in listOf("US" to Locale.US, "UK" to Locale.UK)) {
                                val available = tts.isLanguageAvailable(locale)
                                out += "$label (${locale.toLanguageTag()}): isLanguageAvailable = $available"
                                val voices = tts.voices.orEmpty().filter {
                                    it.locale.language == "en" && it.locale.country == locale.country
                                }
                                if (voices.isEmpty()) {
                                    out += "   voices: (không có)"
                                } else {
                                    voices.forEach { voice ->
                                        out += "   ${voice.name} | offline=${!voice.isNetworkConnectionRequired} | quality=${voice.quality}"
                                    }
                                }
                            }
                        } else if (engine == null) {
                            out += "engine = null"
                        }
                        lines = out
                    }, "com.google.android.tts")

                    onDispose {
                        engine?.stop()
                        engine?.shutdown()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dev_tts_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    lines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}