package vn.bubbledict.data

import vn.bubbledict.data.db.HistoryDao
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.domain.HistorySink
import vn.bubbledict.domain.NormalizeText
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.LookupResult

/**
 * Ghi mọi lượt tra (kể cả lỗi) vào Room qua [HistoryRepository].
 * Giữ lại cả text gốc (rawText) và text chuẩn hoá (normText) theo spec §7.
 */
class HistoryRecorder(private val repository: HistoryRepository) : HistorySink {

    override suspend fun record(rawText: String, request: LookupRequest, result: LookupResult) {
        repository.record(
            HistoryEntry(
                rawText = rawText,
                normText = request.text.ifBlank { NormalizeText.normalize(rawText) },
                kind = result.kind,
                source = request.source,
                resultText = result.displayText,
                sourceLang = "en",
                targetLang = "vi",
                engine = result.engine,
                provider = result.provider,
                state = result.state,
                errorCode = result.errorCode,
                sourceApp = request.sourceApp,
                createdAt = result.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )
        // Prune nằm trong HistoryRepository.record (giữ 1000 mục).
    }

    override suspend fun updateProvider(normText: String, createdAt: Long, provider: String) {
        repository.updateProvider(normText = normText, createdAt = createdAt, provider = provider)
    }

    companion object {
        val MAX_HISTORY = HistoryDao.MAX_HISTORY
    }
}