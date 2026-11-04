package vn.bubbledict.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.LookupRequest

/**
 * Chuỗi nguồn dịch online theo chốt của Chief: **mặc định phải chạy được không cần key**,
 * key chỉ là tuỳ chọn nâng chất lượng.
 *
 * Thứ tự do nơi dựng truyền vào, ví dụ:
 * `MyMemoryTranslator` (keyless) → `GeminiClient`/`OpenAiCompatibleTranslator` (nếu user nhập key)
 * → pipeline tự rơi tiếp xuống ML Kit on-device.
 *
 * Quy tắc:
 * - [FailureReason.NO_KEY] = provider chưa được cấu hình -> **bỏ qua**, không coi là lỗi.
 * - Lỗi khác (HTTP/timeout/mạng/E5) -> thử provider kế tiếp và nhớ lỗi "đáng báo" nhất.
 * - Hết chuỗi: trả lỗi đáng báo nhất theo thứ tự HTTP > TIMEOUT > IO > E5; nếu tất cả đều chưa
 *   cấu hình thì trả `NO_KEY` để pipeline chuyển sang nhánh offline (E3a/E3b).
 * - Huỷ coroutine được truyền ra ngoài, không bị nuốt.
 */
class ChainedTranslator(
    private val sources: List<OnlineTranslator>,
    private val logger: (String) -> Unit = {},
    private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS,
) : OnlineTranslator {

    constructor(vararg sources: OnlineTranslator) : this(sources.toList())

    override suspend fun lookup(request: LookupRequest): TranslationOutcome {
        // BE-20: tổng thời gian online ≤ 10 giây, kể cả khi nhiều provider.
        return try {
            withTimeout(totalTimeoutMs) { lookupSources(request) }
        } catch (e: TimeoutCancellationException) {
            logger("chain deadline exceeded")
            TranslationOutcome.Failure(HttpErrors.E4, FailureReason.TIMEOUT)
        }
    }

    private suspend fun lookupSources(request: LookupRequest): TranslationOutcome {
        var bestFailure: TranslationOutcome.Failure? = null
        var sawNonQuota = false

        for ((index, source) in sources.withIndex()) {
            val outcome = try {
                source.lookup(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger("source $index failed: ${e.javaClass.simpleName}")
                TranslationOutcome.Failure(HttpErrors.E4, FailureReason.IO)
            }

            when (outcome) {
                is TranslationOutcome.Word,
                is TranslationOutcome.Translation,
                -> return outcome

                is TranslationOutcome.Failure -> {
                    if (outcome.reason == FailureReason.NO_KEY ||
                        outcome.reason == FailureReason.NOT_APPLICABLE
                    ) {
                        logger("source $index skipped: ${outcome.reason}")
                        continue
                    }
                    if (outcome.reason != FailureReason.QUOTA) sawNonQuota = true
                    if (bestFailure == null || rank(outcome) > rank(bestFailure)) {
                        bestFailure = outcome
                    }
                }
            }
        }

        // Không có provider nào được cấu hình -> để pipeline đi nhánh offline.
        val failure = bestFailure
            ?: return TranslationOutcome.Failure(HttpErrors.E4, FailureReason.NO_KEY)

        // Spec 1.16 / BUG-16: E19 chỉ khi **tất cả** nguồn keyless đều trả QUOTA. Chỉ cần một
        // nguồn hỏng hạ tầng (5xx/timeout/IO) hoặc trả kết quả rác lẫn vào -> E28 "nguồn tạm
        // không dùng được", không hứa "hết lượt" cho user.
        if (failure.reason == FailureReason.QUOTA && sawNonQuota) {
            logger("mixed quota+unavailable -> unavailable")
            return TranslationOutcome.Failure(
                failure.code,
                FailureReason.UNAVAILABLE,
                httpStatus = failure.httpStatus,
            )
        }
        return failure
    }

    companion object {
        /** Deadline tổng cho toàn chain (BE-20). */
        const val TOTAL_TIMEOUT_MS = 10_000L
    }

    /** Lỗi càng "nói được cho user" càng cao: HTTP/quota > timeout > mạng > kết quả rỗng/sai ngôn ngữ. */
    private fun rank(failure: TranslationOutcome.Failure): Int = when (failure.reason) {
        // Sai key (401/403) dễ hành động hơn "hết quota" -> ưu tiên báo.
        FailureReason.HTTP ->
            if (failure.httpStatus == 401 || failure.httpStatus == 403) 8 else 6
        FailureReason.QUOTA -> 7
        /** Cặp ngôn ngữ không hỗ trợ: vẫn thử nguồn kế trước, nhưng thấp hơn lỗi hạ tầng. */
        FailureReason.PAIR_UNSUPPORTED -> 3
        /** Nguồn hỏng hạ tầng khi có mạng (5xx/timeout/IO) — trên IO nhưng dưới QUOTA. */
        FailureReason.UNAVAILABLE -> 6
        FailureReason.TIMEOUT -> 5
        FailureReason.IO -> 4
        FailureReason.EMPTY -> 3
        FailureReason.NOT_VIETNAMESE -> 2
        FailureReason.PARSE -> 1
        FailureReason.NO_KEY -> 0
        FailureReason.NOT_APPLICABLE -> 0
    }
}