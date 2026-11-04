package vn.bubbledict.domain.model

/**
 * Một lượt tra được yêu cầu (F1/F2/F3). `text` đã chuẩn hoá theo [vn.bubbledict.domain.NormalizeText].
 *
 * - [source] chỉ để hiển thị/lọc trong lịch sử, không vào cache key.
 * - [sourceApp] tên app nguồn khi đi qua ACTION_PROCESS_TEXT; null khi OCR.
 * - [readOnly] từ `EXTRA_PROCESS_TEXT_READONLY`: MVP không sửa text của app nguồn.
 */
data class LookupRequest(
    val text: String,
    val source: TextSource,
    val sourceApp: String? = null,
    val readOnly: Boolean = false,
) {
    val kind: TextKind get() = vn.bubbledict.domain.TextClassifier.classify(text)
}