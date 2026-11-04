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
    /**
     * E28: có mạng nhưng mọi nguồn keyless (MyMemory + 2 Mozhi) đều chết/timeout/5xx.
     * Khác E3a/E3b: KHÔNG dùng copy "Bật mạng", máy đang có mạng.
     */
    ERROR_SOURCES_DOWN,
    /**
     * E3c (spec 1.21): máy CÓ mạng nhưng thiếu model offline cho tra từ / không có nguồn
     * online phù hợp. Dùng layout S5, copy riêng "Chưa tải gói offline để tra từ" —
     * khác E3a (mất mạng thật mới nhắc "Bật mạng").
     */
    ERROR_MODEL_MISSING,
    /**
     * E30: thiếu model ML Kit cho CẶP ngôn ngữ (pivot qua EN có thể cần 2 gói).
     * Cùng layout S5 với [ERROR_MODEL_MISSING]; khác copy. Khi cả E3c và E30 đúng
     * thì pipeline trả E30 (cụ thể hơn) — FE không tự rẽ nhánh.
     */
    ERROR_LANG_MODEL_MISSING,
    /** E31: cặp ngôn ngữ ngoài phạm vi (5 ngôn ngữ) — dùng S7 + lối mở Cài đặt. */
    ERROR_PAIR_UNSUPPORTED,
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
    /** F14: cặp ngôn ngữ của lượt tra (ISO thường: en/vi/zh/ko/ja). */
    val sourceLang: String = "en",
    val targetLang: String = "vi",
    /** E29: không chắc ngôn ngữ nguồn -> chip hiện "? → VI". */
    val sourceLangUncertain: Boolean = false,
    /** F14: nguồn = đích (provider "same-language") -> hiện nguyên văn + dòng giải thích. */
    val sameLanguage: Boolean = false,
    /** E11: mode sửa text OCR trong cùng sheet. */
    val editing: Boolean = false,
    val editText: String = "",
) {
    val isWord: Boolean get() = kind == TextKind.WORD
    val isOcr: Boolean get() = source == TextSource.OCR
    /** F14: TTS chỉ có giọng tiếng Anh -> text nguồn khác EN ẩn nút Nghe im lặng. */
    val ttsAvailableForSource: Boolean get() = sourceLang.equals("en", ignoreCase = true)

    /** F14: WORD nguồn ZH/KO/JA không có IPA/định nghĩa tiếng Anh để hiện. */
    val showsEnglishDetails: Boolean get() = sourceLang.equals("en", ignoreCase = true)

    val canSpeak: Boolean get() = ttsReady && ttsAvailableForSource && originalText.isNotBlank()
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