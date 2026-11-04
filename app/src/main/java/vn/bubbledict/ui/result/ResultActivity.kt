package vn.bubbledict.ui.result

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.bubbledict.R
import vn.bubbledict.domain.NormalizeText
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.ui.main.MainActivity
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * F1 — cửa sổ kết quả qua `ACTION_PROCESS_TEXT`, theme translucent (BE-11).
 *
 * FE-03: nối [ResultSheet] + [ResultViewModel]. Đọc intent/parse giữ nguyên từ khung BE-11:
 * `EXTRA_PROCESS_TEXT` (app ngoài) hoặc extra nội bộ của [ResultActivityContract] (OCR).
 * Đóng sheet = `finish()` để user về đúng app đang đọc (AC5).
 */
class ResultActivity : ComponentActivity() {

    private val viewModel: ResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!handleIntent(intent)) {
            // E1/AC6: text rỗng hoặc chỉ khoảng trắng -> không hiện sheet, nhưng phải báo
            // cho user biết (trước đây finish() im lặng — BUG-02). Dùng applicationContext
            // để toast không bị huỷ cùng activity.
            Toast.makeText(
                applicationContext,
                getString(R.string.error_empty_text),
                Toast.LENGTH_SHORT,
            ).show()
            finish()
            return
        }
        setContent {
            BubbleDictTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        val message = when (event) {
                            ResultEvent.Copied -> getString(
                                if (viewModel.state.value.isWord) {
                                    R.string.copy_done_word
                                } else {
                                    R.string.copy_done_sentence
                                },
                            )

                            ResultEvent.Saved -> getString(R.string.save_done)
                            ResultEvent.Unsaved -> getString(R.string.unsave_done)
                            ResultEvent.SaveFailed -> getString(R.string.result_save_failed)
                            ResultEvent.TtsFailed -> getString(R.string.tts_error)
                            ResultEvent.TtsFallback -> getString(R.string.tts_fallback)
                            ResultEvent.TtsTruncated -> getString(R.string.tts_truncated)
                        }
                        Toast.makeText(this@ResultActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                if (state.originalText.isNotBlank()) {
                    ResultSheet(
                        state = state,
                        onDismissRequest = { finish() },
                        actions = ResultActions(
                            onSave = viewModel::save,
                            onCopy = viewModel::copy,
                            onSpeakToggle = viewModel::toggleSpeak,
                            onRetry = viewModel::retry,
                            onDownloadModel = viewModel::downloadModel,
                            onVoiceChange = viewModel::changeVoice,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onOpenTtsSettings = {
                                vn.bubbledict.ui.settings.openTtsInstall(this)
                            },
                            onOpenApiKeySettings = {
                                startActivity(
                                    Intent(this@ResultActivity, MainActivity::class.java)
                                        .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS),
                                )
                                finish()
                            },
                            onUseUsVoice = {
                                viewModel.changeVoice(Voice.US)
                                viewModel.toggleSpeak()
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Nhận 1 trong 3 nguồn: lịch sử (không tra lại), từ vựng (đã có kết quả), hoặc text mới. */
    private fun handleIntent(target: Intent?): Boolean {
        if (target == null) return false

        val historyId = target.getLongExtra(ResultActivityContract.EXTRA_HISTORY_ID, -1L)
        if (historyId > 0L) {
            viewModel.openHistory(historyId)
            return true
        }

        val savedResult = target.getStringExtra(ResultActivityContract.EXTRA_RESULT_TEXT)
        val savedText = target.getStringExtra(ResultActivityContract.EXTRA_TEXT)
        if (savedResult != null && savedText != null) {
            viewModel.showSaved(savedText, savedResult)
            return true
        }

        val parsed = parseRequest(target) ?: return false
        if (parsed.text.isBlank()) return false
        viewModel.submit(parsed)
        return true
    }

    /** Đọc text từ PROCESS_TEXT của app khác hoặc extra nội bộ (OCR). */
    private fun parseRequest(intent: Intent?): LookupRequest? {
        if (intent == null) return null

        val internalText = intent.getStringExtra(ResultActivityContract.EXTRA_TEXT)
        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val raw = internalText ?: processText ?: return null

        val source = when (intent.getStringExtra(ResultActivityContract.EXTRA_SOURCE)) {
            TextSource.OCR.name -> TextSource.OCR
            TextSource.SELECTION.name -> TextSource.SELECTION
            else -> if (internalText != null) TextSource.OCR else TextSource.SELECTION
        }

        return LookupRequest(
            text = NormalizeText.normalize(raw),
            source = source,
            sourceApp = callingAppPackage(),
            readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false),
        )
    }

    @Suppress("DEPRECATION")
    private fun callingAppPackage(): String? =
        runCatching { callingPackage }.getOrNull()
}