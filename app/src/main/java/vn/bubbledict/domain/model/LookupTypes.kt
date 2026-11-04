package vn.bubbledict.domain.model

/**
 * Kiểu dùng chung cho lõi tra (BE-04/BE-10) và lớp lưu trữ (BE-05).
 *
 * Quan hệ theo plan §4:
 * - [TextKind] chọn engine + cache key. PARAGRAPH thêm theo chốt O3.
 * - [TextSource] chỉ để hiển thị/lọc, **không** vào cache key (cùng text tra từ chọn hay OCR dùng chung cache).
 * - [Engine] và [ResultState] là hai field tách nhau theo chốt O4, không gộp:
 *   cache -> (CACHED, CACHE); online keyless -> (OK, KEYLESS); online có key -> (OK, KEY); offline -> (OK, MLKIT); lỗi -> (ERROR, null).
 */
enum class TextKind { WORD, SENTENCE, PARAGRAPH }

enum class TextSource { SELECTION, OCR }

enum class Engine {
    /** Kết quả lấy từ cache (state = CACHED). */
    CACHE,

    /** Nguồn online không cần key (MyMemory) — spec 1.7. */
    KEYLESS,

    /** Nguồn online có key (Gemini/Groq/OpenAI-compatible). */
    KEY,

    /** Dịch on-device bằng ML Kit. */
    MLKIT,
}

enum class ResultState { OK, ERROR, CACHED }