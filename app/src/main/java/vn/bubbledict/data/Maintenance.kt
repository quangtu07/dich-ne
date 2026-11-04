package vn.bubbledict.data

import vn.bubbledict.data.prefs.SettingsStore

/**
 * BE-17 — dọn dữ liệu định kỳ, chạy khi mở app (background, không chặn UI):
 * - cache quá TTL 7 ngày + vượt cap 2000 dòng,
 * - history cũ hơn `historyRetentionDays` (mặc định 90) + vượt 1000 mục,
 * - nút "Xoá dữ liệu" xoá history + cache nhưng **không** xoá từ vựng (AC12).
 *
 * Idempotent: chạy nhiều lần không mất thêm dữ liệu còn hạn.
 */
class Maintenance(
    private val history: HistoryRepository,
    private val cache: CacheRepository,
    private val vocab: VocabRepository,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Gọi 1 lần khi app mở; an toàn khi gọi lại. */
    suspend fun runOnAppOpen(): Report {
        // AC36 (spec 1.18): xoá email liên hệ MyMemory còn sót từ bản ≤1.0.3 (privacy release gate).
        settings.purgeLegacyContactEmail()

        val retentionDays = settings.current().historyRetentionDays.coerceAtLeast(1)
        val cutoff = now() - retentionDays * DAY_MS

        val deletedHistory = history.deleteOlderThan(cutoff)
        val prunedHistory = history.prune()
        cache.sweep()

        return Report(
            deletedOldHistory = deletedHistory,
            prunedHistory = prunedHistory,
            historyCount = history.count(),
            cacheCount = cache.count(),
        )
    }

    /** Nút "Xoá dữ liệu": xoá lịch sử + cache, giữ từ vựng đã lưu. */
    suspend fun clearUserData() {
        history.clear()
        cache.clear()
    }

    /** Settings "Xoá lịch sử": chỉ lịch sử, giữ cache + từ vựng (design §3.7). */
    suspend fun clearHistory(): Int = history.clear()

    /** Settings "Xoá cache": chỉ cache, giữ lịch sử + từ vựng. */
    suspend fun clearCache(): Int = cache.clear()

    /** Dung lượng cache thật (byte) cho item "Xoá cache" — thay số hardcode "12,4 MB". */
    suspend fun cacheSizeBytes(): Long = cache.sizeBytes()

    /** Xoá từ vựng (nút riêng trong Settings). */
    suspend fun clearVocab(): Int = vocab.all().sumOf { entry ->
        vocab.remove(entry.text)
    }

    data class Report(
        val deletedOldHistory: Int,
        val prunedHistory: Int,
        val historyCount: Int,
        val cacheCount: Int,
    )

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}