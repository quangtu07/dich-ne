package vn.bubbledict.data.remote

/**
 * CR-04 / BE-28 — **một chỗ duy nhất** phân loại lỗi của nguồn dịch, để BUG-16 (5xx bị dán nhãn
 * "hết lượt") không tái phát rải rác trong từng client.
 *
 * Luật spec 1.16/1.17:
 * - **QUOTA** = provider thật sự hết hạn mức: `429` (với provider có quota) hoặc body
 *   `quotaFinished=true`. Chỉ khi MỌI nguồn keyless đều quota thì pipeline mới báo E19.
 * - **UNAVAILABLE** = nguồn tạm không dùng được: `5xx`, `429` của provider **không có** quota
 *   (Mozhi bọc upstream 429 thành 500), IO/timeout/host chết, query quá dài. Pipeline trả E28
 *   khi máy có mạng, E3a/E3b khi thật sự mất mạng.
 * - **HTTP** = lỗi phía request/key (`400/401/403`...); 401/403 -> E20 ở pipeline.
 */
object SourceFailure {

    fun quota(httpStatus: Int? = null): TranslationOutcome.Failure =
        TranslationOutcome.Failure(HttpErrors.E4, FailureReason.QUOTA, httpStatus = httpStatus)

    fun unavailable(httpStatus: Int? = null): TranslationOutcome.Failure =
        TranslationOutcome.Failure(HttpErrors.E4, FailureReason.UNAVAILABLE, httpStatus = httpStatus)

    fun http(status: Int): TranslationOutcome.Failure =
        TranslationOutcome.Failure(HttpErrors.E4, FailureReason.HTTP, httpStatus = status)

    /**
     * Map status HTTP không-2xx của một client.
     *
     * @param hasQuota false = provider không có hạn mức riêng (Mozhi) -> 429 cũng là UNAVAILABLE.
     */
    fun fromHttpStatus(status: Int, hasQuota: Boolean): TranslationOutcome.Failure = when {
        status == 429 -> if (hasQuota) quota(status) else unavailable(status)
        status in 500..599 -> unavailable(status)
        else -> http(status)
    }
}