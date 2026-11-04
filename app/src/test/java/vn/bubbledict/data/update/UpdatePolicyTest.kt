package vn.bubbledict.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F12 — AC33 (bỏ qua) và AC34 (1 lần/ngày) là luật thuần, test không cần Android. */
class UpdatePolicyTest {

    private val day = UpdatePolicy.CHECK_INTERVAL_MS

    @Test
    fun neverChecked_shouldCheck() {
        assertTrue(UpdatePolicy.shouldCheck(nowMillis = 1_000L, lastCheckAtMillis = 0L))
    }

    @Test
    fun checkedWithinOneDay_shouldNotCheck() {
        val now = 10 * day
        assertFalse(UpdatePolicy.shouldCheck(now, lastCheckAtMillis = now - day / 2))
        assertFalse(UpdatePolicy.shouldCheck(now, lastCheckAtMillis = now - 1))
    }

    @Test
    fun checkedExactlyOneDayAgo_shouldCheckAgain() {
        val now = 10 * day
        assertTrue(UpdatePolicy.shouldCheck(now, lastCheckAtMillis = now - day))
    }

    @Test
    fun banner_onlyForNewerVersion() {
        assertTrue(UpdatePolicy.shouldShowBanner(manifestVersionCode = 3, currentVersionCode = 2, skippedVersionCode = 0))
        assertFalse(UpdatePolicy.shouldShowBanner(manifestVersionCode = 2, currentVersionCode = 2, skippedVersionCode = 0))
        assertFalse(UpdatePolicy.shouldShowBanner(manifestVersionCode = 1, currentVersionCode = 2, skippedVersionCode = 0))
    }

    @Test
    fun banner_hiddenAfterSkipSameVersion_butBackForNewer() {
        assertFalse(
            "đã bấm Bỏ qua bản 3 thì không hiện lại bản 3",
            UpdatePolicy.shouldShowBanner(manifestVersionCode = 3, currentVersionCode = 2, skippedVersionCode = 3),
        )
        assertTrue(
            "bản 4 vẫn phải hiện dù đã bỏ qua bản 3",
            UpdatePolicy.shouldShowBanner(manifestVersionCode = 4, currentVersionCode = 2, skippedVersionCode = 3),
        )
    }
}