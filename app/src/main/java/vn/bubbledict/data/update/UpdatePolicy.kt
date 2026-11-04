package vn.bubbledict.data.update

/**
 * F12 — luật hiển thị/kiểm cập nhật, tách khỏi Android để test JVM.
 *
 * AC34: tối đa 1 lần kiểm/ngày. AC33: version user đã "Bỏ qua" thì không hiện banner nữa.
 */
object UpdatePolicy {

    /** 24 giờ. */
    const val CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    /** Chưa từng kiểm (0) hoặc đã qua [intervalMs] kể từ lần trước. */
    fun shouldCheck(
        nowMillis: Long,
        lastCheckAtMillis: Long,
        intervalMs: Long = CHECK_INTERVAL_MS,
    ): Boolean = lastCheckAtMillis <= 0L || nowMillis - lastCheckAtMillis >= intervalMs

    /** Có banner khi manifest mới hơn bản đang cài và user chưa bấm "Bỏ qua" đúng version đó. */
    fun shouldShowBanner(
        manifestVersionCode: Int,
        currentVersionCode: Int,
        skippedVersionCode: Int,
    ): Boolean =
        manifestVersionCode > currentVersionCode && manifestVersionCode != skippedVersionCode
}