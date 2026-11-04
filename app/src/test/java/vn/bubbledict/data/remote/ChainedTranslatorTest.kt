package vn.bubbledict.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.bubbledict.domain.OnlineTranslator
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.TextSource

/**
 * Chuỗi nguồn dịch: bỏ qua provider chưa cấu hình (NO_KEY), fallback khi lỗi,
 * trả lỗi đáng báo nhất khi tất cả fail, truyền huỷ coroutine.
 */
class ChainedTranslatorTest {

    private val request = LookupRequest("The city was quiet.", TextSource.SELECTION)

    private class FakeSource(
        private val outcome: TranslationOutcome? = null,
        private val throwable: Throwable? = null,
    ) : OnlineTranslator {
        var calls = 0
        override suspend fun lookup(request: LookupRequest): TranslationOutcome {
            calls++
            throwable?.let { throw it }
            return outcome!!
        }
    }

    private fun failure(reason: FailureReason, status: Int? = null) =
        TranslationOutcome.Failure(HttpErrors.E4, reason, httpStatus = status)

    @Test
    fun firstSuccess_stopsChain() = runBlocking {
        val first = FakeSource(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val second = FakeSource(TranslationOutcome.Translation("không dùng"))
        val chain = ChainedTranslator(listOf(first, second))

        val outcome = chain.lookup(request)

        assertEquals(TranslationOutcome.Translation("Thành phố yên tĩnh."), outcome)
        assertEquals(1, first.calls)
        assertEquals(0, second.calls)
    }

    @Test
    fun unconfiguredSource_isSkipped() = runBlocking {
        val keyless = FakeSource(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val needsKey = FakeSource(failure(FailureReason.NO_KEY))
        val chain = ChainedTranslator(listOf(needsKey, keyless))

        val outcome = chain.lookup(request)

        assertTrue(outcome is TranslationOutcome.Translation)
        assertEquals(1, needsKey.calls)
        assertEquals(1, keyless.calls)
    }

    @Test
    fun httpError_fallsBackToNextSource() = runBlocking {
        val first = FakeSource(failure(FailureReason.HTTP, 500))
        val second = FakeSource(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val chain = ChainedTranslator(listOf(first, second))

        assertTrue(chain.lookup(request) is TranslationOutcome.Translation)
        assertEquals(1, second.calls)
    }

    @Test
    fun timeout_fallsBackToNextSource() = runBlocking {
        val first = FakeSource(failure(FailureReason.TIMEOUT))
        val second = FakeSource(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val chain = ChainedTranslator(listOf(first, second))

        assertTrue(chain.lookup(request) is TranslationOutcome.Translation)
    }

    @Test
    fun allFail_returnsMostInformativeError() = runBlocking {
        val first = FakeSource(failure(FailureReason.NOT_VIETNAMESE))
        val second = FakeSource(failure(FailureReason.HTTP, 429))
        val chain = ChainedTranslator(listOf(first, second))

        val outcome = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.HTTP, outcome.reason)
        assertEquals(429, outcome.httpStatus)
    }

    @Test
    fun allUnconfigured_returnsNoKey() = runBlocking {
        val chain = ChainedTranslator(
            listOf(FakeSource(failure(FailureReason.NO_KEY)), FakeSource(failure(FailureReason.NO_KEY))),
        )

        val outcome = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.NO_KEY, outcome.reason)
    }

    @Test
    fun exceptionInSource_continuesWithNext() = runBlocking {
        val broken = FakeSource(throwable = IllegalStateException("boom"))
        val good = FakeSource(TranslationOutcome.Translation("Thành phố yên tĩnh."))
        val chain = ChainedTranslator(listOf(broken, good))

        assertTrue(chain.lookup(request) is TranslationOutcome.Translation)
        assertEquals(1, good.calls)
    }

    @Test(expected = CancellationException::class)
    fun cancellation_isPropagated() {
        val chain = ChainedTranslator(listOf(FakeSource(throwable = CancellationException("stop"))))

        runBlocking { chain.lookup(request) }
    }

    @Test
    fun deadline_stopsSlowChain() = runBlocking {
        val slow = object : OnlineTranslator {
            override suspend fun lookup(request: LookupRequest): TranslationOutcome {
                delay(2_000)
                return TranslationOutcome.Translation("không kịp")
            }
        }
        val chain = ChainedTranslator(listOf(slow), totalTimeoutMs = 100)

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.TIMEOUT, failure.reason)
    }

    @Test
    fun quotaThenNoKey_returnsQuotaNotOffline() = runBlocking {
        val keyless = FakeSource(failure(FailureReason.QUOTA, 429))
        val needsKey = FakeSource(failure(FailureReason.NO_KEY))
        val chain = ChainedTranslator(listOf(keyless, needsKey))

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.QUOTA, failure.reason)
    }

    @Test
    fun allSourcesQuota_returnsQuota() = runBlocking {
        val chain = ChainedTranslator(
            listOf(
                FakeSource(failure(FailureReason.QUOTA, 429)),
                FakeSource(failure(FailureReason.QUOTA, 429)),
            ),
        )

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        // Spec 1.16 / AC25a: E19 chỉ khi MỌI nguồn keyless đều trả QUOTA.
        assertEquals(FailureReason.QUOTA, failure.reason)
    }

    @Test
    fun mixedQuotaAndUnavailable_returnsUnavailable() = runBlocking {
        // Đúng ca BUG-16: MyMemory hết lượt + Mozhi chết (5xx) -> phải là "nguồn tạm không dùng
        // được" (E28), không được hứa "hết lượt" (E19).
        val chain = ChainedTranslator(
            listOf(
                FakeSource(failure(FailureReason.QUOTA, 429)),
                FakeSource(failure(FailureReason.UNAVAILABLE, 500)),
            ),
        )

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
    }

    @Test
    fun mixedQuotaAndIo_returnsUnavailable() = runBlocking {
        val chain = ChainedTranslator(
            listOf(
                FakeSource(failure(FailureReason.QUOTA, 429)),
                FakeSource(failure(FailureReason.IO)),
            ),
        )

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
    }

    @Test
    fun allSourcesPairUnsupported_returnsPairUnsupported() = runBlocking {
        val chain = ChainedTranslator(
            listOf(
                FakeSource(failure(FailureReason.PAIR_UNSUPPORTED, 400)),
                FakeSource(failure(FailureReason.PAIR_UNSUPPORTED, 422)),
            ),
        )

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.PAIR_UNSUPPORTED, failure.reason)
    }

    @Test
    fun pairUnsupportedThenUnavailable_keepsUnavailable() = runBlocking {
        // Lỗi hạ tầng (5xx) đáng báo hơn "cặp không hỗ trợ".
        val chain = ChainedTranslator(
            listOf(
                FakeSource(failure(FailureReason.PAIR_UNSUPPORTED, 400)),
                FakeSource(failure(FailureReason.UNAVAILABLE, 500)),
            ),
        )

        val failure = chain.lookup(request) as TranslationOutcome.Failure

        assertEquals(FailureReason.UNAVAILABLE, failure.reason)
    }
}
