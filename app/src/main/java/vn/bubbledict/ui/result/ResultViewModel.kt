package vn.bubbledict.ui.result

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.data.db.entity.VocabEntry
import vn.bubbledict.domain.TextClassifier
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.domain.model.TtsVoice

/**
 * FE-03 — ViewModel cửa sổ kết quả.
 *
 * Đã nối: nhận [LookupRequest] (phân loại kind từ BE-04), giữ text gốc + source, hiển thị S1
 * loading, copy text, phát âm bằng TTS hệ thống (tạm thời tới khi BE-12 `TtsSpeaker` xong),
 * nhớ lựa chọn giọng vào DataStore (AC19).
 *
 * Đã nối `AppContainer.lookupPipeline` (BE-10) → `LookupResult` → [ResultUiState] qua
 * `ResultUiMapper`; mọi dependency (settings/secret/pipeline/offline/repo) lấy từ container.
 */
class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as BubbleDictApp).container
    private val settingsStore = container.settingsStore
    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private val historyRepository = container.historyRepository
    private val vocabRepository = container.vocabRepository
    private val pipeline = container.lookupPipeline
    private val offlineTranslator = container.offlineTranslator
    private val _events = MutableSharedFlow<ResultEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ResultEvent> = _events

    private var request: LookupRequest? = null
    private var lastResult: vn.bubbledict.domain.model.LookupResult? = null
    private var lookupJob: Job? = null
    private val ttsSpeaker = container.ttsSpeaker

    init {
        // BE-12: hỏi thẳng TtsSpeaker xem máy có giọng en-US/en-GB không (AC18/E14).
        viewModelScope.launch {
            val us = ttsSpeaker.usAvailable()
            val uk = ttsSpeaker.ukAvailable()
            _state.value = _state.value.copy(
                ttsReady = us || uk,
                usAvailable = us,
                ukAvailable = uk,
            )
        }

        viewModelScope.launch {
            val saved = settingsStore.current()
            _state.value = _state.value.copy(
                voice = if (saved.ttsVoice.equals("UK", ignoreCase = true)) Voice.UK else Voice.US,
            )
        }
    }

    /** Nhận lượt tra mới (F1 intent hoặc OCR). Huỷ kết quả cũ, đưa về S1 loading. */
    fun submit(newRequest: LookupRequest) {
        request = newRequest
        val previous = _state.value
        _state.value = ResultUiState(
            status = ResultStatus.LOADING,
            kind = newRequest.kind,
            source = newRequest.source,
            originalText = newRequest.text,
            voice = previous.voice,
            ttsReady = previous.ttsReady,
            ukAvailable = previous.ukAvailable,
            usAvailable = previous.usAvailable,
        )
        stopSpeaking()
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val result = pipeline.lookup(
                rawText = newRequest.text,
                source = newRequest.source,
                sourceApp = newRequest.sourceApp,
            )
            lastResult = result
            val mapped = result.toUiState(
                originalText = newRequest.text,
                source = newRequest.source,
                audio = _state.value,
            )
            _state.value = mapped.copy(
                isSaved = vocabRepository.find(newRequest.text) != null,
            )

            // AC29/E22: nghĩa ML Kit đã hiện trước (trên đây) — giờ enrich IPA/định nghĩa tiếng Anh
            // cho WORD không key, có kết quả thì cập nhật state tại chỗ. Không có gì (E21) thì im.
            if (result.kind == TextKind.WORD &&
                result.state == ResultState.OK &&
                result.engine == Engine.MLKIT
            ) {
                val enriched = runCatching { pipeline.enrichWord(result) }.getOrNull()
                if (enriched != null && request === newRequest) {
                    val current = _state.value
                    _state.value = enriched.toUiState(
                        originalText = newRequest.text,
                        source = newRequest.source,
                        audio = current,
                    ).copy(isSaved = current.isSaved, isExpanded = current.isExpanded)
                }
            }
        }
    }

    /** FE-06: mở lại kết quả đã lưu trong lịch sử, không gọi mạng lại. */
    fun openHistory(id: Long) {
        viewModelScope.launch {
            val entry = historyRepository.findById(id) ?: return@launch
            _state.value = entry.toResultState()
        }
    }

    /** FE-07: xem từ đã lưu (chế độ "Đã lưu", không tra lại). */
    fun showSaved(text: String, meaning: String) {
        val previous = _state.value
        _state.value = ResultUiState(
            status = ResultStatus.SUCCESS,
            kind = TextClassifier.classify(text),
            source = TextSource.SELECTION,
            originalText = text,
            translation = meaning,
            isSaved = true,
            voice = previous.voice,
            ttsReady = previous.ttsReady,
            ukAvailable = previous.ukAvailable,
            usAvailable = previous.usAvailable,
        )
    }

    private fun HistoryEntry.toResultState(): ResultUiState {
        val previous = _state.value
        val lowerCode = errorCode.orEmpty()
        val status = when {
            state == ResultState.ERROR && lowerCode == "E5" -> ResultStatus.ERROR_EMPTY
            state == ResultState.ERROR && lowerCode == "E3" -> ResultStatus.ERROR_NO_NETWORK_LONG
            state == ResultState.ERROR -> ResultStatus.ERROR_SOURCE
            state == ResultState.CACHED -> ResultStatus.CACHED
            else -> ResultStatus.SUCCESS
        }
        val label = Instant.ofEpochMilli(createdAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm dd-MM", Locale("vi", "VN")))
        return ResultUiState(
            status = status,
            kind = kind,
            source = source,
            originalText = normText,
            translation = resultText,
            cachedAtLabel = if (status == ResultStatus.CACHED) label else null,
            voice = previous.voice,
            ttsReady = previous.ttsReady,
            ukAvailable = previous.ukAvailable,
            usAvailable = previous.usAvailable,
            isSaved = false,
        )
    }

    fun copy() {
        val text = _state.value.resultText.ifBlank { _state.value.originalText }
        if (text.isBlank()) return
        val clipboard = getApplication<Application>()
            .getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("bubble-dict", text))
        viewModelScope.launch { _events.emit(ResultEvent.Copied) }
    }

    /**
     * AC18–AC22: `speak()` của BE-12 là suspend, chạy trong viewModelScope; bấm lần 2 gọi
     * `stop()` chứ không chồng thêm lượt đọc.
     */
    fun toggleSpeak() {
        val text = _state.value.originalText
        if (text.isBlank()) return
        if (_state.value.isSpeaking) {
            ttsSpeaker.stop()
            _state.value = _state.value.copy(isSpeaking = false)
            return
        }
        _state.value = _state.value.copy(isSpeaking = true)
        viewModelScope.launch {
            val outcome = ttsSpeaker.speak(text, _state.value.voice.toTtsVoice())
            _state.value = _state.value.copy(isSpeaking = false)
            if (!outcome.completed && outcome.errorCode != null) {
                _events.emit(ResultEvent.TtsFailed)
            } else if (outcome.spokenWithFallback) {
                outcome.spokenVoice?.let { spoken ->
                    _state.value = _state.value.copy(voice = spoken.toUiVoice())
                }
                _events.emit(ResultEvent.TtsFallback)
            } else if (outcome.truncated) {
                _events.emit(ResultEvent.TtsTruncated)
            }
        }
    }

    private fun Voice.toTtsVoice(): TtsVoice =
        if (this == Voice.UK) TtsVoice.UK else TtsVoice.US

    private fun TtsVoice.toUiVoice(): Voice =
        if (this == TtsVoice.UK) Voice.UK else Voice.US

    fun changeVoice(voice: Voice) {
        _state.value = _state.value.copy(voice = voice)
        viewModelScope.launch { settingsStore.setTtsVoice(voice.name) }
    }

    fun retry() {
        request?.let(::submit)
    }

    /** UX-02 §4.1: "Xem thêm"/"Thu gọn" text gốc ngay trong sheet. */
    /**
     * S11: tải gói dịch offline (BE-09) ngay trong sheet. Tải xong (thành công hay lỗi thì
     * chỉ thử lại **đúng 1 lần** — không vòng lặp tải/thử.
     */
    fun downloadModel() {
        if (_state.value.status == ResultStatus.DOWNLOADING_MODEL) return
        _state.value = _state.value.copy(status = ResultStatus.DOWNLOADING_MODEL)
        viewModelScope.launch {
            offlineTranslator.ensureModelDownloaded()
            val current = request
            if (current != null && _state.value.status == ResultStatus.DOWNLOADING_MODEL) {
                submit(current)
            }
        }
    }

    fun toggleExpanded() {
        _state.value = _state.value.copy(isExpanded = !_state.value.isExpanded)
    }

    /** Lưu/bỏ lưu từ vào Từ vựng (AC12). Chỉ áp dụng khi đã có kết quả là từ. */
    fun save() {
        val current = _state.value
        val result = lastResult
        val text = request?.text ?: current.originalText
        if (!current.isWord || text.isBlank()) return
        viewModelScope.launch {
            if (current.isSaved) {
                vocabRepository.remove(text)
                _state.value = current.copy(isSaved = false)
                _events.emit(ResultEvent.Unsaved)
            } else {
                val meaning = result?.displayText ?: current.resultText
                if (meaning.isBlank()) {
                    _events.emit(ResultEvent.SaveFailed)
                    return@launch
                }
                vocabRepository.save(
                    VocabEntry(
                        text = text,
                        meaning = meaning,
                        ipa = result?.ipa,
                        savedAt = System.currentTimeMillis(),
                    ),
                )
                _state.value = current.copy(isSaved = true)
                _events.emit(ResultEvent.Saved)
            }
        }
    }

    private fun stopSpeaking() {
        if (_state.value.isSpeaking) {
            ttsSpeaker.stop()
            _state.value = _state.value.copy(isSpeaking = false)
        }
    }

    override fun onCleared() {
        // TtsSpeaker thuộc AppContainer (sống theo app), không shutdown ở đây.
        stopSpeaking()
        super.onCleared()
    }
}

/** Sự kiện ngắn để Activity báo bằng Toast/Snackbar (snackbar trong sheet sẽ làm ở P2). */
enum class ResultEvent { Copied, Saved, Unsaved, SaveFailed, TtsFailed, TtsFallback, TtsTruncated }
