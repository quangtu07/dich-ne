package vn.bubbledict.domain.model

/**
 * Trạng thái gói dịch offline ML Kit en→vi, khớp 5 state trong UX-03 §3.5.
 * Lưu vào DataStore bằng tên enum (đọc giá trị lạ -> NOT_DOWNLOADED).
 */
enum class OfflineModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED,
    PAUSED_NETWORK,
    ;

    companion object {
        fun fromName(name: String?): OfflineModelState =
            entries.firstOrNull { it.name == name } ?: NOT_DOWNLOADED
    }
}