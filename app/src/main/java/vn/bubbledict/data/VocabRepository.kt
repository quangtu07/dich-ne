package vn.bubbledict.data

import androidx.room.withTransaction
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.VocabDao
import vn.bubbledict.data.db.entity.VocabEntry

/**
 * Từ vựng đã lưu (AC12). Không phụ thuộc history: xoá lịch sử không xoá vocab.
 * Lưu trùng `text` thì cập nhật bản ghi cũ (giữ `id`), không tạo bản ghi trùng.
 */
class VocabRepository(private val db: AppDatabase) {

    private val dao: VocabDao = db.vocabDao()

    /**
     * Lưu hoặc cập nhật theo `text`. Trả `id` thật của dòng (insert mới hoặc dòng cũ).
     *
     * Không gọi thẳng `@Upsert`: với `id = 0` Room sinh `INSERT OR IGNORE` + `UPDATE WHERE id = 0`,
     * trùng `text` sẽ không cập nhật gì. Ở đây đọc theo `text` rồi insert/update trong transaction.
     */
    suspend fun save(entry: VocabEntry): Long = db.withTransaction {
        val existing = dao.findByText(entry.text)
        if (existing == null) {
            dao.insert(entry)
        } else {
            dao.update(entry.copy(id = existing.id))
            existing.id
        }
    }

    suspend fun all(): List<VocabEntry> = dao.all()

    suspend fun find(text: String): VocabEntry? = dao.findByText(text)

    suspend fun count(): Int = dao.count()

    suspend fun remove(text: String): Int = dao.deleteByText(text)
}