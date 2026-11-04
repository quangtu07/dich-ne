package vn.bubbledict.data

import androidx.room.withTransaction
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.HistoryDao
import vn.bubbledict.data.db.entity.HistoryEntry

/**
 * Ghi/đọc lịch sử trên Room. Mỗi lượt tra đi qua [record] để insert + prune nguyên tử,
 * tránh trường hợp DB vượt 1000 mục khi insert dồn dập.
 *
 * Hoàn tác xoá (snackbar 5s): [undoDelete] chèn lại đúng entity cũ (id giữ nguyên) —
 * `history.id` là AUTOINCREMENT nên id đã xoá không bị cấp lại, không lo trùng PK.
 */
class HistoryRepository(private val db: AppDatabase) {

    private val dao: HistoryDao = db.historyDao()

    suspend fun record(entry: HistoryEntry): Long = db.withTransaction {
        dao.insert(entry).also { dao.pruneTo(HistoryDao.MAX_HISTORY) }
    }

    /** Giống [record] nhưng trả về entry kèm id đã cấp. */
    suspend fun recordAndReturn(entry: HistoryEntry): HistoryEntry =
        entry.copy(id = record(entry))

    suspend fun recent(limit: Int = 100): List<HistoryEntry> = dao.recent(limit)

    suspend fun search(query: String, limit: Int = 100): List<HistoryEntry> = dao.search(query, limit)

    suspend fun findById(id: Long): HistoryEntry? = dao.findById(id)

    suspend fun count(): Int = dao.count()

    suspend fun delete(id: Long): Int = dao.deleteById(id)

    suspend fun clear(): Int = dao.deleteAll()

    /** Chèn lại mục vừa xoá cho snackbar hoàn tác; trả id (giữ nguyên id cũ). */
    suspend fun undoDelete(entry: HistoryEntry): Long = record(entry)

    /** BE-17: xoá mục cũ hơn [cutoff]; gọi khi mở app. Không đụng vocab/cache. */
    suspend fun deleteOlderThan(cutoff: Long): Int = dao.deleteOlderThan(cutoff)

    /** Cập nhật provider của lượt đã ghi (sau enrich IPA/định nghĩa); trả số dòng sửa. */
    suspend fun updateProvider(normText: String, createdAt: Long, provider: String): Int =
        dao.updateProvider(normText, createdAt, provider)

    /** Giữ tối đa [HistoryDao.MAX_HISTORY] mục mới nhất; trả số dòng đã xoá. */
    suspend fun prune(): Int = dao.pruneTo(HistoryDao.MAX_HISTORY)
}