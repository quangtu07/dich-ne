package vn.bubbledict.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Từ đã lưu. Tách khỏi `history` để xoá lịch sử không mất từ vựng (AC12).
 * Unique theo `text` (so khớp chính xác sau khi chuẩn hoá ở tầng gọi).
 */
@Entity(
    tableName = "vocab",
    indices = [Index(value = ["text"], unique = true)],
)
data class VocabEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "meaning") val meaning: String,
    @ColumnInfo(name = "ipa") val ipa: String? = null,
    /** JSON array nghĩa/ví dụ, giữ nguyên payload để không phải migrate khi schema đổi. */
    @ColumnInfo(name = "examplesJson") val examplesJson: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "savedAt") val savedAt: Long,
)