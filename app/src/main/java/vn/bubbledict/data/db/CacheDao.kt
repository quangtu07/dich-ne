package vn.bubbledict.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import vn.bubbledict.data.db.entity.CacheEntry

/**
 * Cache kết quả dịch. Chỉ lượt thành công mới ghi (plan §3.1).
 * TTL 7 ngày + cap 2000 dòng do [vn.bubbledict.data.CacheRepository] (BE-07) và BE-17 gọi.
 */
@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun count(): Int

    /** Dung lượng payload đang giữ (byte, xấp xỉ) — cho item "Xoá cache" ở Settings. */
    @Query("SELECT COALESCE(SUM(LENGTH(payloadJson)), 0) FROM cache")
    suspend fun sizeBytes(): Long

    @Query("DELETE FROM cache WHERE `key` = :key")
    suspend fun delete(key: String): Int

    @Query("DELETE FROM cache WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /** Giữ [cap] dòng mới nhất; trả số dòng đã xoá. */
    @Query(
        "DELETE FROM cache WHERE `key` NOT IN " +
            "(SELECT `key` FROM cache ORDER BY createdAt DESC LIMIT :cap)",
    )
    suspend fun pruneTo(cap: Int): Int

    @Query("DELETE FROM cache")
    suspend fun deleteAll(): Int
}