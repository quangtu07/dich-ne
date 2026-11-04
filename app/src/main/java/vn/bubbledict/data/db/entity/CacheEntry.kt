package vn.bubbledict.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.TextKind

/**
 * Cache kết quả dịch. `key = sha256(normText.lowercase() + "|" + targetLang + "|" + kind)`
 * (BE-07 dựng key; entity chỉ giữ). `source` cố ý không có trong key.
 */
@Entity(
    tableName = "cache",
    indices = [Index("createdAt")],
)
data class CacheEntry(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "normText") val normText: String,
    @ColumnInfo(name = "targetLang") val targetLang: String,
    @ColumnInfo(name = "kind") val kind: TextKind,
    /** Payload JSON đã serialise (senses/examples/translation) để không phụ thuộc cột DB. */
    @ColumnInfo(name = "payloadJson") val payloadJson: String,
    @ColumnInfo(name = "engine") val engine: Engine,
    /** Nguồn tạo payload (gemini/mymemory/openai-compatible/mlkit). */
    @ColumnInfo(name = "provider") val provider: String? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)