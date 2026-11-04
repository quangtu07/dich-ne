package vn.bubbledict.data.remote

/** Vì sao một lượt gọi Gemini thất bại — pipeline (BE-10) map sang mã lỗi/thang offline. */
enum class FailureReason {
    /** Chưa cấu hình key trong Settings. */
    NO_KEY,
    /** Provider không phù hợp loại text (MyMemory không dùng cho WORD) — chain bỏ qua, không tính lỗi. */
    NOT_APPLICABLE,
    /** Hết hạn mức miễn phí của provider (MyMemory quotaFinished / 429) — rơi xuống ML Kit. */
    QUOTA,
    /** HTTP không 2xx (429, 5xx, 400/403 key sai...). */
    HTTP,
    /** Quá 10 giây (E4). */
    TIMEOUT,
    /** Lỗi mạng/IO khác (UnknownHost...) — pipeline coi như offline để chuyển ML Kit (E3). */
    IO,
    /** Trả về rỗng hoặc không đọc được (E5). */
    EMPTY,
    /** Trả về text không phải tiếng Việt (E5). */
    NOT_VIETNAMESE,
    /** JSON không khớp schema (E5). */
    PARSE,
}

/**
 * Kết quả một lượt gọi **một nguồn dịch online** (Gemini, MyMemory, OpenAI-compatible...).
 * Tên cũ `TranslationOutcome` giữ làm typealias để code cũ không phải sửa hàng loạt.
 */
sealed interface TranslationOutcome {

    data class Word(
        val payload: WordPayload,
        /** Tên nguồn trả kết quả ("gemini", "mymemory", "openai-compatible") — lưu vào DB §7. */
        val provider: String? = null,
    ) : TranslationOutcome

    data class Translation(
        val text: String,
        val provider: String? = null,
    ) : TranslationOutcome

    data class Failure(
        /** Mã lỗi hiển thị cho user: [HttpErrors.E4] hoặc [HttpErrors.E5]. */
        val code: String,
        val reason: FailureReason,
        val httpStatus: Int? = null,
    ) : TranslationOutcome
}

/**
 * Mã lỗi nguồn dịch theo spec §5.1:
 * - E4: 429/5xx/timeout > 10 giây -> "thử lại", log nội bộ.
 * - E5: rỗng hoặc không phải tiếng Việt -> "Không có kết quả".
 *
 * Không đưa URL/key vào message/log (CR-01).
 */
object HttpErrors {
    const val E4 = "E4"
    const val E5 = "E5"

    fun isSourceError(status: Int): Boolean = status == 429 || status in 500..599
}