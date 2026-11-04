package vn.bubbledict.ui.result

import vn.bubbledict.domain.LookupErrorCodes
import vn.bubbledict.domain.model.LookupResult
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextSource

/**
 * Map `LookupResult` (BE-10) → [ResultUiState] cho sheet (10 state S1–S10, UX-02 §5).
 *
 * Quy ước (spec v1.4 + O2/O4):
 * - CACHED → S3, chip "Kết quả đã lưu" + giờ tra.
 * - OK + engine MLKIT → S4 "Kết quả offline" + dòng phụ chất lượng thấp.
 * - E3a → S5 (thiếu gói offline), E3b → S6 (đoạn dài cần mạng), E4 → S7, E5 → S8, E1 → S8.
 * - E2 (truncatedTo != null) → S9 chip "Đã rút gọn…" (ưu tiên hơn trạng thái nền).
 * - WORD không có nghĩa → S10 "Không có trong từ điển" khi E5.
 */
fun LookupResult.toUiState(
    originalText: String,
    source: TextSource,
    audio: ResultUiState,
): ResultUiState {
    val baseStatus = when {
        truncatedTo != null -> ResultStatus.TRUNCATED
        state == ResultState.ERROR -> when (errorCode) {
            LookupErrorCodes.E3A -> ResultStatus.ERROR_NO_NETWORK_SHORT
            // E3c: có mạng nhưng thiếu model offline / không có nguồn online phù hợp.
            LookupErrorCodes.E3C -> ResultStatus.ERROR_MODEL_MISSING
            // E30: thiếu gói ngoại tuyến cho cặp ngôn ngữ (F14, pivot qua EN -> có thể 2 gói).
            LookupErrorCodes.E30 -> ResultStatus.ERROR_LANG_MODEL_MISSING
            // E31 (F14): cặp ngôn ngữ ngoài 5 ngôn ngữ hỗ trợ.
            "E31" -> ResultStatus.ERROR_PAIR_UNSUPPORTED
            LookupErrorCodes.E3B -> ResultStatus.ERROR_NO_NETWORK_LONG
            LookupErrorCodes.E4 -> ResultStatus.ERROR_SOURCE
            // E28: có mạng nhưng cả chain keyless chết (5xx/timeout) — không phải hết quota.
            "E28" -> ResultStatus.ERROR_SOURCES_DOWN
            "E19" -> ResultStatus.ERROR_QUOTA
            "E20" -> ResultStatus.ERROR_KEY_INVALID
            LookupErrorCodes.E5 -> ResultStatus.ERROR_EMPTY
            LookupErrorCodes.E1 -> ResultStatus.ERROR_EMPTY
            else -> ResultStatus.ERROR_SOURCE
        }

        state == ResultState.CACHED -> ResultStatus.CACHED
        // E22: kết quả ML Kit không phải tiếng Việt — vẫn là S4 nhưng không hiện "nghĩa" giả.
        errorCode == "E22" -> ResultStatus.OFFLINE_RESULT
        engine == vn.bubbledict.domain.model.Engine.MLKIT -> ResultStatus.OFFLINE_RESULT
        // F11: kết quả keyless (MyMemory) và có key (Gemini/OpenAI-compatible) đều là S2 —
        // Designer chốt không hiện nhãn provider trong UI.
        engine == vn.bubbledict.domain.model.Engine.KEYLESS ||
            engine == vn.bubbledict.domain.model.Engine.KEY -> ResultStatus.SUCCESS
        state == ResultState.OK -> ResultStatus.SUCCESS
        else -> ResultStatus.ERROR_SOURCE
    }

    return ResultUiState(
        status = baseStatus,
        kind = kind,
        sourceLang = sourceLang,
        targetLang = targetLang,
        sourceLangUncertain = sourceLangUncertain,
        sameLanguage = provider == "same-language",
        source = source,
        originalText = originalText,
        translation = displayText?.takeUnless { errorCode == "E22" },
        ipa = ipa,
        senses = senses.mapIndexed { index, sense ->
            SenseUi(
                index = index + 1,
                meaning = stripEnglishPartOfSpeech(sense.meaning, sense.partOfSpeech),
                partOfSpeech = vietnamesePartOfSpeech(sense.partOfSpeech),
            )
        },
        examples = examples.map { ExampleUi(text = it.text, translation = it.translation) },
        synonyms = synonyms,
        englishDefinitions = englishDefinitions,
        noVietnameseMeaning = errorCode == "E22",
        cachedAtLabel = if (baseStatus == ResultStatus.CACHED) {
            formatCachedAt(createdAt)
        } else {
            null
        },
        truncatedPrefixLength = truncatedTo,
        voice = audio.voice,
        ttsReady = audio.ttsReady,
        ukAvailable = audio.ukAvailable,
        usAvailable = audio.usAvailable,
    )
}

/**
 * Designer: nghĩa WORD không được trộn tiếng Anh. ML Kit/LLM trả POS tiếng Anh
 * ("adjective", "noun"...) -> map sang nhãn tiếng Việt; loại không map được thì bỏ hẳn
 * thay vì hiện tiếng Anh.
 */
private val PART_OF_SPEECH_VI = mapOf(
    "adjective" to "tính từ",
    "noun" to "danh từ",
    "verb" to "động từ",
    "adverb" to "trạng từ",
    "pronoun" to "đại từ",
    "preposition" to "giới từ",
    "conjunction" to "liên từ",
    "interjection" to "thán từ",
)

internal fun vietnamesePartOfSpeech(raw: String?): String? {
    val key = raw?.trim()?.lowercase() ?: return null
    return PART_OF_SPEECH_VI[key]
}

/** Bỏ tiền tố loại từ tiếng Anh nếu nguồn nhét vào chính câu nghĩa: "(adjective) hào hùng". */
internal fun stripEnglishPartOfSpeech(meaning: String, partOfSpeech: String?): String {
    val pos = partOfSpeech?.trim()?.lowercase()
    if (pos.isNullOrEmpty()) return meaning
    val prefixPatterns = listOf("($pos)", "[$pos]", "$pos:", "$pos.")
    for (prefix in prefixPatterns) {
        if (meaning.startsWith(prefix, ignoreCase = true)) {
            return meaning.substring(prefix.length).trim()
        }
    }
    return meaning
}

private fun formatCachedAt(createdAt: Long): String? {
    if (createdAt <= 0L) return null
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(createdAt)
        .atZone(zone)
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd-MM", java.util.Locale("vi", "VN")))
}
