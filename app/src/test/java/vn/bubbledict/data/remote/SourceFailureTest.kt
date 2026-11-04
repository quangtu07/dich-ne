package vn.bubbledict.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CR-04 / BE-28: một chỗ duy nhất phân loại lỗi HTTP của nguồn dịch (chống tái phát BUG-16).
 */
class SourceFailureTest {

    @Test
    fun providerWithQuota_429_isQuota() {
        assertEquals(FailureReason.QUOTA, SourceFailure.fromHttpStatus(429, hasQuota = true).reason)
    }

    @Test
    fun providerWithoutQuota_429_isUnavailable() {
        // Mozhi: 429 = instance quá tải, KHÔNG phải hết lượt của user.
        assertEquals(FailureReason.UNAVAILABLE, SourceFailure.fromHttpStatus(429, hasQuota = false).reason)
    }

    @Test
    fun any5xx_isUnavailable() {
        for (status in listOf(500, 502, 503, 504)) {
            assertEquals(
                "status $status phải là UNAVAILABLE",
                FailureReason.UNAVAILABLE,
                SourceFailure.fromHttpStatus(status, hasQuota = true).reason,
            )
        }
    }

    @Test
    fun clientErrors_stayHttp() {
        for (status in listOf(400, 401, 403)) {
            assertEquals(FailureReason.HTTP, SourceFailure.fromHttpStatus(status, hasQuota = true).reason)
        }
    }

    @Test
    fun quotaHelper_keepsStatus() {
        assertEquals(429, SourceFailure.quota(429).httpStatus)
    }
}