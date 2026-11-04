package vn.bubbledict.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vn.bubbledict.data.db.entity.CacheEntry
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.data.db.entity.VocabEntry

/**
 * DB duy nhất của app: lịch sử, từ vựng, cache. **Không** chứa API key (key ở SecretStore).
 * Schema export sang `app/schemas` để review migration.
 */
@Database(
    entities = [HistoryEntry::class, VocabEntry::class, CacheEntry::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    abstract fun vocabDao(): VocabDao

    abstract fun cacheDao(): CacheDao

    companion object {
        const val DB_NAME = "bubble-dict.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /** v1 -> v2: thêm cột `provider` (nguồn trả kết quả) cho history + cache. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN provider TEXT")
                db.execSQL("ALTER TABLE cache ADD COLUMN provider TEXT")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}