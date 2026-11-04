package vn.bubbledict.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import vn.bubbledict.data.db.entity.VocabEntry

/**
 * Từ vựng đã lưu. Unique theo `text`.
 *
 * **Không dùng `@Upsert`**: entity có `id = 0` (autoGenerate) nên khi trùng `text` Room sinh
 * `INSERT OR IGNORE` + `UPDATE ... WHERE id = 0`, tức là không cập nhật dòng nào
 * (đã kiểm chứng bằng instrumented test). Upsert đúng nằm ở [vn.bubbledict.data.VocabRepository.save]:
 * đọc theo `text` rồi insert/update trong cùng transaction.
 */
@Dao
interface VocabDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: VocabEntry): Long

    @Update
    suspend fun update(entry: VocabEntry)

    @Query("SELECT * FROM vocab ORDER BY savedAt DESC, id DESC")
    suspend fun all(): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE id = :id")
    suspend fun findById(id: Long): VocabEntry?

    @Query("SELECT * FROM vocab WHERE text = :text")
    suspend fun findByText(text: String): VocabEntry?

    @Query("SELECT COUNT(*) FROM vocab")
    suspend fun count(): Int

    @Delete
    suspend fun delete(entry: VocabEntry)

    @Query("DELETE FROM vocab WHERE text = :text")
    suspend fun deleteByText(text: String): Int
}