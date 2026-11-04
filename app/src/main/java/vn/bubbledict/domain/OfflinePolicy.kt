package vn.bubbledict.domain

/**
 * Ngưỡng offline O1 (Chief chốt): dùng ML Kit khi text **≤ 200 ký tự VÀ ≤ 3 câu**.
 * Vượt một trong hai -> E3b "Cần mạng để dịch đoạn này".
 *
 * Đếm câu = số dấu `.` `!` `?` (tối thiểu 1 nếu có chữ), khớp với heuristic classifier BE-04.
 */
object OfflinePolicy {

    const val MAX_OFFLINE_CHARS = 200
    const val MAX_OFFLINE_SENTENCES = 3

    fun sentenceCount(text: String): Int {
        if (text.isBlank()) return 0
        return maxOf(1, text.count { it == '.' || it == '!' || it == '?' })
    }

    fun canTranslateOffline(text: String): Boolean =
        text.length <= MAX_OFFLINE_CHARS && sentenceCount(text) <= MAX_OFFLINE_SENTENCES
}