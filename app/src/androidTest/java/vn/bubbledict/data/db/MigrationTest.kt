package vn.bubbledict.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB v1 -> v2: thêm cột `provider` cho `history` và `cache` (spec 1.7 §7).
 * Test xác nhận migration giữ nguyên dữ liệu cũ và cột mới mặc định NULL.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesRows_andAddsProvider() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO history (rawText, normText, kind, source, resultText, sourceLang, targetLang,
                                     engine, state, errorCode, sourceApp, createdAt)
                VALUES ('ubiquitous', 'ubiquitous', 'WORD', 'SELECTION', 'có mặt khắp nơi', 'en', 'vi',
                        'GEMINI', 'OK', NULL, NULL, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO cache (`key`, normText, targetLang, kind, payloadJson, engine, createdAt)
                VALUES ('k1', 'ubiquitous', 'vi', 'WORD', '{"translation":"có mặt khắp nơi"}', 'GEMINI', 1700000000000)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT rawText, provider FROM history").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("ubiquitous", cursor.getString(0))
            assertNull("cột provider mặc định NULL", cursor.getString(1))
        }
        db.query("SELECT `key`, provider FROM cache").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("k1", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        db.close()
    }
}