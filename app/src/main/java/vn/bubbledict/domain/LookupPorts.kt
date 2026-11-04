package vn.bubbledict.domain

import vn.bubbledict.domain.model.CachedPayload
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.LookupResult
import vn.bubbledict.domain.model.TextKind

/**
 * Cổng ra của [LookupPipeline] (để test bằng fake, không cần mạng/Room/ML Kit).
 * Implement thật: `GeminiClient` (online), `MlkitOfflineTranslator` (offline),
 * `CacheRepository` (cache), `HistoryRecorder` (lịch sử).
 */
interface OnlineTranslator {
    suspend fun lookup(request: LookupRequest): vn.bubbledict.data.remote.TranslationOutcome
}

interface OfflineTranslator {
    /** Gói dịch ML Kit en→vi đã tải xong và dùng được. */
    suspend fun isModelReady(): Boolean

    /** Dịch offline; null/failure nghĩa là không dùng được (pipeline trả E3a). */
    suspend fun translate(text: String): String?
}

interface LookupCache {
    suspend fun read(normText: String, targetLang: String, kind: TextKind): CachedPayload?

    suspend fun write(
        normText: String,
        targetLang: String,
        kind: TextKind,
        payload: CachedPayload,
        engine: Engine,
    )
}

interface HistorySink {
    /**
     * Ghi mọi lượt tra (kể cả lỗi) theo spec §3.1.
     * `request.text` là text đã chuẩn hoá (và đã cắt nếu E2).
     */
    suspend fun record(rawText: String, request: LookupRequest, result: LookupResult)

    /**
     * AC29: enrich xong thì cập nhật lại nguồn thật của lượt ĐÃ ghi (BUG-08).
     * Default no-op để fake trong test không phải cài đặt.
     */
    suspend fun updateProvider(normText: String, createdAt: Long, provider: String) = Unit
}