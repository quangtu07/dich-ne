package vn.bubbledict.ui.result

import androidx.compose.runtime.Immutable
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * Model UI cho cửa sổ kết quả (C3).
 *
 * Enum loại text/source dùng thẳng từ `domain/model/LookupTypes.kt` (BE-04) để không có
 * 2 bộ enum trùng nghĩa. [ResultStatus] là trạng thái *hiển thị* (10 state S1–S10 của
 * docs/design/result-sheet.md §5); BE-10 map `LookupResult` (+ `ResultState`/`Engine`) sang đây
 * trong `ResultViewModel` (FE-03). Không đưa Room/network model vào Composable.
 */

/** Giọng phát âm; mirror `ttsVoice` trong DataStore (US mặc định). */
enum class Voice { US, UK }

/** Đủ 10 state S1–S10 trong docs/design/result-sheet.md §5. */
enum class ResultStatus {
    LOADING,
    SUCCESS,
    CACHED,
    OFFLINE_RESULT,
    ERROR_NO_NETWORK_SHORT,
    ERROR_NO_NETWORK_LONG,
    ERROR_SOURCE,
    ERROR_EMPTY,
    TRUNCATED,
    WORD_NOT_FOUND,
    /** E19/AC25: nguồn keyless hết quota — gợi ý nhập key, không báo "cần mạng". */
    ERROR_QUOTA,
    /** E20: key sai 401/403 — pipeline đã rơi về nguồn khác; chỉ báo nhẹ. */
    ERROR_KEY_INVALID,
    /** S11 (UX-02 §5): đang tải gói dịch offline ngay trong sheet; chỉ còn nút X. */
    DOWNLOADING_MODEL,
}

@Immutable
data class SenseUi(
    val index: Int,
    val meaning: String,
    /** Từ loại hiện trước nghĩa nếu có, ví dụ "(n)". */
    val partOfSpeech: String? = null,
)

@Immutable
data class ExampleUi(
    val text: String,
    val translation: String? = null,
)

@Immutable
data class ResultUiState(
    val status: ResultStatus = ResultStatus.LOADING,
    val kind: TextKind = TextKind.WORD,
    val source: TextSource = TextSource.SELECTION,
    val originalText: String = "",
    val translation: String? = null,
    val ipa: String? = null,
    val senses: List<SenseUi> = emptyList(),
    val examples: List<ExampleUi> = emptyList(),
    val synonyms: List<String> = emptyList(),
    /** AC28: định nghĩa tiếng Anh từ dictionaryapi.dev khi không có key; tối đa 2 hiển thị. */
    val englishDefinitions: List<String> = emptyList(),
    /** E22: ML Kit trả output không phải tiếng Việt -> không hiện làm nghĩa, chỉ hiện định nghĩa EN. */
    val noVietnameseMeaning: Boolean = false,
    /** Nhãn "Tra lúc 14:03 hôm nay" khi status = CACHED. */
    val cachedAtLabel: String? = null,
    /** E2: số ký tự đầu còn lại sau khi cắt; null = không cắt. */
    val truncatedPrefixLength: Int? = null,
    val isExpanded: Boolean = false,
    val isSaved: Boolean = false,
    val isSpeaking: Boolean = false,
    val voice: Voice = Voice.US,
    val ukAvailable: Boolean = true,
    val usAvailable: Boolean = true,
    /** false = máy chưa có TTS tiếng Anh nào (E14, cả 2 giọng thiếu). */
    val ttsReady: Boolean = true,
    /** E11: mode sửa text OCR trong cùng sheet. */
    val editing: Boolean = false,
    val editText: String = "",
) {
    val isWord: Boolean get() = kind == TextKind.WORD
    val isOcr: Boolean get() = source == TextSource.OCR
    val canSpeak: Boolean get() = ttsReady && originalText.isNotBlank()
    val canSave: Boolean get() = isWord && originalText.isNotBlank()
    val canCopy: Boolean get() = originalText.isNotBlank()

    /** Text đưa vào clipboard: nghĩa/bản dịch, không phải text gốc (UX-02 §6). */
    val resultText: String
        get() = translation ?: senses.joinToString("; ") { it.meaning }
}

/** Callback từ UI lên ViewModel/Activity; mặc định no-op để preview được. */
@Immutable
data class ResultActions(
    val onSave: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onSpeakToggle: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDownloadModel: () -> Unit = {},
    val onVoiceChange: (Voice) -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onStartEdit: () -> Unit = {},
    val onEditTextChange: (String) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onSubmitEdit: () -> Unit = {},
    val onOpenTtsSettings: () -> Unit = {},
    val onUseUsVoice: () -> Unit = {},
    /** E19: mở Settings để nhập API key (không ép, chỉ gợi ý). */
    val onOpenApiKeySettings: () -> Unit = {},
)