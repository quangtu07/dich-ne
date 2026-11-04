package vn.bubbledict.data

import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.CacheDao
import vn.bubbledict.data.db.entity.CacheEntry
import vn.bubbledict.domain.LookupCache
import vn.bubbledict.domain.model.CachedPayload
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.TextKind

/**
 * Cache kết quả dịch. Chỉ lượt **thành công** mới ghi (plan §3.1).
 *
 * - TTL 7 ngày: quá hạn coi như miss và xoá luôn dòng đó.
 * - Cap 2000 dòng: insert xong prune trong cùng transaction để không phình DB.
 * - Đồng hồ injectable ([now]) để test TTL không phải fake thời gian hệ thống.
 */
class CacheRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LookupCache {

    private val dao: CacheDao = db.cacheDao()

    private val cacheJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun get(normText: String, targetLang: String, kind: TextKind): CacheEntry? {
        val key = CacheKey.of(normText, targetLang, kind)
        val entry = dao.get(key) ?: return null
        if (now() - entry.createdAt >= TTL_MS) {
            dao.delete(key) // hết hạn thì dọn luôn, đừng để rác
            return null
        }
        return entry
    }

    /** Lưu kết quả thành công; trả key đã dùng. Prune cap trong cùng transaction. */
    suspend fun put(
        normText: String,
        targetLang: String,
        kind: TextKind,
        payloadJson: String,
        engine: Engine,
        provider: String? = null,
    ): String = db.withTransaction {
        val key = CacheKey.of(normText, targetLang, kind)
        dao.put(
            CacheEntry(
                key = key,
                normText = CacheKey.normalize(normText),
                targetLang = targetLang,
                kind = kind,
                payloadJson = payloadJson,
                engine = engine,
                provider = provider,
                createdAt = now(),
            ),
        )
        dao.pruneTo(MAX_CACHE)
        key
    }

    /** Port cho pipeline: đọc payload đã parse, tự kiểm TTL (hết hạn coi như miss). */
    override suspend fun read(normText: String, targetLang: String, kind: TextKind): CachedPayload? {
        val key = CacheKey.of(normText, targetLang, kind)
        val entry = dao.get(key) ?: return null
        if (now() - entry.createdAt >= TTL_MS) {
            dao.delete(key)
            return null
        }
        return runCatching { cacheJson.decodeFromString<CachedPayload>(entry.payloadJson) }.getOrNull()
    }

    /** Port cho pipeline: chỉ lượt thành công online mới gọi hàm này. */
    override suspend fun write(
        normText: String,
        targetLang: String,
        kind: TextKind,
        payload: CachedPayload,
        engine: Engine,
    ) {
        put(
            normText = normText,
            targetLang = targetLang,
            kind = kind,
            payloadJson = cacheJson.encodeToString(CachedPayload.serializer(), payload),
            engine = engine,
            provider = payload.provider,
        )
    }

    suspend fun count(): Int = dao.count()

    /** Xấp xỉ dung lượng payload cache (byte) để hiện ở Settings; không đụng UI. */
    suspend fun sizeBytes(): Long = dao.sizeBytes()

    /** BE-17: gọi khi mở app — xoá quá TTL rồi prune cap. */
    suspend fun sweep() {
        dao.deleteOlderThan(now() - TTL_MS)
        dao.pruneTo(MAX_CACHE)
    }

    suspend fun clear(): Int = dao.deleteAll()

    companion object {
        const val TTL_DAYS = 7
        const val TTL_MS = TTL_DAYS * 24L * 60L * 60L * 1000L
        const val MAX_CACHE = 2000
    }
}