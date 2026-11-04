package vn.bubbledict.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * Một lượt tra. Mọi lượt (kể cả lỗi) đều ghi vào đây (plan §4).
 *
 * `engine` nullable: lượt lỗi không có engine ([ResultState.ERROR] -> null).
 * `id` autoGenerate: với AUTOINCREMENT id đã xoá không bị cấp lại, nên hoàn tác xoá
 * (snackbar 5s) chỉ cần chèn lại đúng entity cũ với `id` tường minh.
 */
@Entity(
    tableName = "history",
    indices = [Index("createdAt"), Index("normText")],
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Text gốc như app nguồn trả về. */
    @ColumnInfo(name = "rawText") val rawText: String,
    /** Text đã chuẩn hoá, dùng để tìm và hiển thị trong lịch sử. */
    @ColumnInfo(name = "normText") val normText: String,
    @ColumnInfo(name = "kind") val kind: TextKind,
    @ColumnInfo(name = "source") val source: TextSource,
    /** Nghĩa/bản dịch để hiển thị lại; lỗi thì null. */
    @ColumnInfo(name = "resultText") val resultText: String? = null,
    @ColumnInfo(name = "sourceLang") val sourceLang: String = "en",
    @ColumnInfo(name = "targetLang") val targetLang: String = "vi",
    @ColumnInfo(name = "engine") val engine: Engine? = null,
    @ColumnInfo(name = "state") val state: ResultState,
    /** Mã lỗi E1–E17 khi state = ERROR. */
    @ColumnInfo(name = "errorCode") val errorCode: String? = null,
    /** Nguồn trả kết quả: "gemini", "mymemory", "openai-compatible", "mlkit" (§7 spec 1.6). */
    @ColumnInfo(name = "provider") val provider: String? = null,
    /** Tên app nguồn (F1) nếu lấy được; null khi OCR. */
    @ColumnInfo(name = "sourceApp") val sourceApp: String? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)