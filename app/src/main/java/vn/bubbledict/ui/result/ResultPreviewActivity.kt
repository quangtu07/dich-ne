package vn.bubbledict.ui.result

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.bubbledict.R
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * Màn dev: render [ResultSheet] với dữ liệu mẫu, dùng để QA chụp 10 state S1–S10
 * (không phải màn hình thật của app). Mở bằng adb:
 *
 *   adb shell am start -n vn.bubbledict/.ui.result.ResultPreviewActivity --es state S5
 *
 * State nhận: S1..S10, SENTENCE, PARAGRAPH, OCR, EDITING, UK_MISSING, TTS_MISSING, SAVED.
 */
class ResultPreviewActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stateName = intent.getStringExtra(EXTRA_STATE) ?: "S2"
        setContent {
            BubbleDictTheme {
                var showSheet by rememberSaveable { mutableStateOf(true) }
                var uiState by remember { mutableStateOf(ResultPreviewData.forState(stateName)) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val copyWord = stringResource(R.string.copy_done_word)
                val copySentence = stringResource(R.string.copy_done_sentence)
                val saveDone = stringResource(R.string.save_done)
                val unsaveDone = stringResource(R.string.unsave_done)
                val ttsError = stringResource(R.string.tts_error)

                Box(Modifier.fillMaxSize()) {
                    ReaderBackground()
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (showSheet) {
                    ResultSheet(
                        state = uiState,
                        onDismissRequest = { showSheet = false },
                        actions = ResultActions(
                            onSave = {
                                uiState = uiState.copy(isSaved = !uiState.isSaved)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (uiState.isSaved) saveDone else unsaveDone,
                                    )
                                }
                            },
                            onCopy = {
                                val text = uiState.resultText
                                if (text.isNotBlank()) {
                                    copyToClipboard(text)
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (uiState.isWord) copyWord else copySentence,
                                        )
                                    }
                                }
                            },
                            onSpeakToggle = {
                                uiState = uiState.copy(isSpeaking = !uiState.isSpeaking)
                                if (!uiState.isSpeaking) {
                                    speak(uiState.originalText, ttsError)
                                }
                            },
                            onRetry = {
                                uiState = uiState.copy(status = ResultStatus.LOADING)
                            },
                            onDownloadModel = {
                                scope.launch { snackbar.showSnackbar("Gói offline sẽ do BE-09 nối vào") }
                            },
                            onVoiceChange = { voice -> uiState = uiState.copy(voice = voice) },
                            onToggleExpanded = {
                                uiState = uiState.copy(isExpanded = !uiState.isExpanded)
                            },
                            onStartEdit = {
                                uiState = uiState.copy(
                                    editing = true,
                                    editText = uiState.originalText,
                                )
                            },
                            onEditTextChange = { value -> uiState = uiState.copy(editText = value) },
                            onCancelEdit = { uiState = uiState.copy(editing = false) },
                            onSubmitEdit = {
                                uiState = uiState.copy(
                                    editing = false,
                                    originalText = uiState.editText,
                                    status = ResultStatus.LOADING,
                                )
                            },
                            onOpenTtsSettings = {
                                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                runCatching { startActivity(intent) }.onFailure {
                                    runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                            },
                            onUseUsVoice = {
                                uiState = uiState.copy(voice = Voice.US)
                                Toast.makeText(context, "Đang đọc bằng giọng Anh-Mỹ", Toast.LENGTH_SHORT).show()
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun speak(text: String, errorMessage: String) {
        if (text.isBlank()) return
        tts?.shutdown()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
            } else {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("bubble-dict", text))
    }

    /** Nền giả lập app đọc sách phía sau để thấy scrim + ngữ cảnh đóng sheet. */
    @Composable
    private fun ReaderBackground() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(6) { index ->
                Text(
                    text = "Reading English books is a great way to learn. It builds vocabulary " +
                        "naturally and keeps you interested in the story. (đoạn nền ${index + 1})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    companion object {
        const val EXTRA_STATE = "state"
    }
}