package vn.bubbledict.domain

/**
 * Cổng bổ sung thông tin từ điển cho WORD khi **không có key** (chốt Chief):
 * ML Kit cho nghĩa tiếng Việt, nguồn keyless (dictionaryapi.dev) cho IPA + định nghĩa tiếng Anh.
 *
 * Chỉ gửi đúng từ đang tra, không gửi câu/đoạn/ảnh. Lỗi mạng -> null, không chặn kết quả.
 */
interface DictionaryEnricher {
    suspend fun enrich(word: String): DictionaryInfo?
}

data class DictionaryInfo(
    val word: String,
    /** Phiên âm IPA (có dấu /.../), null nếu nguồn không có. */
    val ipa: String? = null,
    val partOfSpeech: String? = null,
    /** Định nghĩa tiếng Anh ngắn, theo thứ tự nguồn trả. */
    val definitions: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
)