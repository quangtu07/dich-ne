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
    /**
     * Đã có đủ model ML Kit cho cặp ngôn ngữ chưa (F14/BE-33: model theo từng ngôn ngữ,
     * pivot qua EN). Mặc định `en → vi` giữ hành vi 1.0.4.
     */
    suspend fun isModelReady(sourceLang: String = "en", targetLang: String = "vi"): Boolean

    /** Dịch offline; null/failure nghĩa là không dùng được (pipeline trả E3a/E3c/E30). */
    suspend fun translate(text: String, sourceLang: String = "en", targetLang: String = "vi"): String?
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