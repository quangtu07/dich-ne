package vn.bubbledict.domain.model

import kotlinx.serialization.Serializable

/** Một nghĩa của từ (WORD). */
@Serializable
data class Sense(
    val meaning: String,
    val partOfSpeech: String? = null,
)

/** Ví dụ (WORD): câu tiếng Anh + bản dịch. */
@Serializable
data class Example(
    val text: String,
    val translation: String? = null,
)

/**
 * Payload lưu cache (JSON) — đúng phần "kết quả" chứ không chứa state/engine/thời gian,
 * vì các field đó phụ thuộc lần đọc (cache hit luôn là CACHED/CACHE theo O4).
 */
@Serializable
data class CachedPayload(
    val translation: String? = null,
    val senses: List<Sense> = emptyList(),
    val ipa: String? = null,
    val examples: List<Example> = emptyList(),
    val synonyms: List<String> = emptyList(),
    /** Định nghĩa tiếng Anh (từ điển keyless khi không có key) — UI có thể ẩn/hiện thêm. */
    val englishDefinitions: List<String> = emptyList(),
    /** Tên nguồn đã tạo payload (gemini/mymemory/openai-compatible/mlkit) — đồng bộ với DB §7. */
    val provider: String? = null,
)

/**
 * Kết quả một lượt tra, đủ để UI map sang S2–S10 (FE-03) và ghi lịch sử (BE-05).
 *
 * Theo O4: [state] và [engine] tách nhau —
 * cache -> (CACHED, CACHE); online keyless -> (OK, KEYLESS); online có key -> (OK, KEY); offline -> (OK, MLKIT); lỗi -> (ERROR, null).
 * [errorCode] dùng mã E1/E3a/E3b/E4/E5 của spec §5.1; UI tự map sang chuỗi tiếng Việt.
 *
 * O2: kết quả offline không có phiên âm/ví dụ nên [ipa]/[examples]/[synonyms] rỗng — UI ẩn hẳn hàng,
 * không hiện placeholder.
 */
data class LookupResult(
    val kind: TextKind,
    val state: ResultState,
    val engine: Engine?,
    val translation: String? = null,
    val senses: List<Sense> = emptyList(),
    val ipa: String? = null,
    val examples: List<Example> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val englishDefinitions: List<String> = emptyList(),
    /** Nguồn trả kết quả (spec 1.6 §7); null khi lỗi/không xác định. "same-language" = nguồn=đích. */
    val provider: String? = null,
    /** F14 (BE-34): ngôn ngữ nguồn đã nhận diện (ISO 639-1) — chip "ZH → VI" lấy từ đây. */
    val sourceLang: String = "en",
    /** F14 (BE-34): ngôn ngữ đích của lượt tra — lấy từ Settings lúc tra. */
    val targetLang: String = "vi",
    /** E29: không chắc ngôn ngữ nguồn (text ngắn/trộn script) — UI hiện chip "? → VI". */
    val sourceLangUncertain: Boolean = false,
    /** Text đã chuẩn hoá của lượt tra — cần cho enrich sau (cập nhật cache đúng key). */
    val requestText: String = "",
    val errorCode: String? = null,
    /** E2: số ký tự được giữ lại khi text gốc > 2000; null = không cắt. */
    val truncatedTo: Int? = null,
    val createdAt: Long = 0L,
) {
    val isError: Boolean get() = state == ResultState.ERROR

    /** Text đưa vào clipboard / hiển thị làm bản dịch: ưu tiên translation, rồi ghép nghĩa. */
    val displayText: String?
        get() = translation?.takeIf { it.isNotBlank() }
            ?: senses.joinToString(separator = "; ") { it.meaning }.takeIf { it.isNotBlank() }
}