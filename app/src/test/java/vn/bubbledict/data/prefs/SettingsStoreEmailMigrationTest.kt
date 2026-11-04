package vn.bubbledict.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * AC36 (spec 1.18) — privacy release gate:
 * - email liên hệ MyMemory từng lưu ở bản ≤1.0.3 phải bị **xoá** (migration), không chỉ ẩn UI;
 * - API cũ không được ghi email mới;
 * - `AppSettings.myMemoryContactEmail` luôn rỗng.
 */
@Suppress("DEPRECATION")
class SettingsStoreEmailMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val legacyKey = stringPreferencesKey("myMemoryContactEmail")

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(): Pair<SettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        return SettingsStore(dataStore) to dataStore
    }

    @Test
    fun purge_removesLegacyEmail() = runBlocking {
        val (store, dataStore) = newStore()
        // Giả lập dữ liệu bản ≤1.0.3: email đã lưu trong DataStore.
        dataStore.edit { it[legacyKey] = "old@example.com" }
        assertEquals("old@example.com", dataStore.data.first()[legacyKey])

        store.purgeLegacyContactEmail()

        assertNull("AC36: key email cũ phải bị xoá", dataStore.data.first()[legacyKey])
        assertEquals("AC36: settings không đọc lại email cũ", "", store.current().myMemoryContactEmail)
    }

    @Test
    fun purge_isIdempotent_whenNoLegacyEmail() = runBlocking {
        val (store, dataStore) = newStore()

        store.purgeLegacyContactEmail()
        store.purgeLegacyContactEmail()

        assertNull(dataStore.data.first()[legacyKey])
    }

    @Test
    fun deprecatedSetter_neverStoresEmail() = runBlocking {
        val (store, dataStore) = newStore()

        store.setMyMemoryContactEmail("new@example.com")

        assertNull("AC36: không được ghi email mới", dataStore.data.first()[legacyKey])
        assertEquals("", store.current().myMemoryContactEmail)
    }
}