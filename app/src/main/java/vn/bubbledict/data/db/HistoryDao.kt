package vn.bubbledict.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import vn.bubbledict.data.db.entity.HistoryEntry

/**
 * Lịch sử tra. Ghi mọi lượt (kể cả lỗi); giữ tối đa [MAX_HISTORY] mục gần nhất.
 * Hoàn tác xoá = chèn lại entity cũ với `id` tường minh (id AUTOINCREMENT không bị cấp lại).
 */
@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<HistoryEntry>)

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun findById(id: Long): HistoryEntry?

    @Query("SELECT * FROM history ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<HistoryEntry>

    @Query(
        "SELECT * FROM history WHERE normText LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 100): List<HistoryEntry>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    /** Xoá các mục cũ vượt quá [keep] mục mới nhất; trả số dòng đã xoá. */
    @Query(
        "DELETE FROM history WHERE id NOT IN " +
            "(SELECT id FROM history ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun pruneTo(keep: Int): Int

    /** BE-17: xoá mục cũ hơn [cutoff] (epoch millis). */
    @Query("DELETE FROM history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Delete
    suspend fun delete(entry: HistoryEntry)

    /** BE-22/BUG-08: enrich sau render cập nhật lại nguồn thật của lượt tra. */
    @Query("UPDATE history SET provider = :provider WHERE createdAt = :createdAt AND normText = :normText")
    suspend fun updateProvider(normText: String, createdAt: Long, provider: String): Int

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int

    companion object {
        const val MAX_HISTORY = 1000
    }
}