package vn.bubbledict.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.bubbledict.domain.model.OfflineModelState
import java.io.File
import java.util.UUID

/**
 * BE-06 DoD: cài đặt giữ sau restart; `ttsVoice` mặc định US, đổi UK đọc lại vẫn UK (AC19);
 * key Gemini đọc lại đúng; file lưu key không chứa plaintext.
 */
@RunWith(AndroidJUnit4::class)
class PrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newStore(scope: CoroutineScope, file: File): SettingsStore = SettingsStore(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        ),
    )

    private fun scope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Test
    fun settingsDefault_voiceIsUs() = runBlocking {
        val scope = scope()
        try {
            val store = newStore(scope, File(context.filesDir, "datastore/default_${UUID.randomUUID()}.preferences_pb"))
            val settings = store.current()
            assertEquals("US", settings.ttsVoice)
            assertEquals("vi", settings.targetLang)
            assertEquals(90, settings.historyRetentionDays)
            assertEquals(OfflineModelState.NOT_DOWNLOADED, settings.mlkitModelState)
            assertFalse(settings.bubbleEnabled)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun settingsSurviveRestart() = runBlocking {
        val file = File(context.filesDir, "datastore/restart_${UUID.randomUUID()}.preferences_pb")
        val firstScope = scope()
        newStore(firstScope, file).apply {
            setTtsVoice("UK")
            setTtsRate(0.8f)
            setTargetLang("vi")
            setBubbleEnabled(true)
            setBubblePosition("left", 0.35f)
            setMlkitModelProgress(OfflineModelState.DOWNLOADING, 42, 12_600_000L, 30_000_000L)
        }
        firstScope.coroutineContext[Job]!!.cancelAndJoin()

        val secondScope = scope()
        try {
            val settings = newStore(secondScope, file).current()

            assertEquals("UK", settings.ttsVoice) // AC19 sau restart
            assertEquals(0.8f, settings.ttsRate)
            assertTrue(settings.bubbleEnabled)
            assertEquals("left", settings.bubbleEdge) // AC30: lưu cạnh, không lưu pixel
            assertEquals(0.35f, settings.bubbleYRatio)
            assertEquals(OfflineModelState.DOWNLOADING, settings.mlkitModelState)
            assertEquals(42, settings.mlkitModelProgress)
            assertEquals(30_000_000L, settings.mlkitModelTotalBytes)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun voice_rejectsUnknownValue() = runBlocking {
        val scope = scope()
        try {
            val store = newStore(scope, File(context.filesDir, "datastore/voice_${UUID.randomUUID()}.preferences_pb"))
            store.setTtsVoice("vi-VN")
            assertEquals("US", store.current().ttsVoice)
            store.setTtsVoice("uk")
            assertEquals("UK", store.current().ttsVoice)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun secretStore_saveReadClear() {
        val prefsName = "secrets_test_${UUID.randomUUID()}"
        val store = EncryptedSecretStore(context, prefsName)

        assertNull(store.geminiApiKey())
        assertFalse(store.saveGeminiApiKey("   "))
        assertTrue(store.saveGeminiApiKey("  AIzaFAKE_TEST_KEY_123  "))

        // Đọc lại qua instance mới (như mở lại app).
        val reopened = EncryptedSecretStore(context, prefsName)
        assertEquals("AIzaFAKE_TEST_KEY_123", reopened.geminiApiKey())

        reopened.clear()
        assertNull(EncryptedSecretStore(context, prefsName).geminiApiKey())
    }

    @Test
    fun secretFile_doesNotContainPlaintextKey() {
        val prefsName = "secrets_plain_${UUID.randomUUID()}"
        val store = EncryptedSecretStore(context, prefsName)
        store.saveGeminiApiKey("AIzaPLAINTEXT_SHOULD_NOT_APPEAR")

        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$prefsName.xml")
        assertTrue("file prefs phải tồn tại: ${prefsFile.absolutePath}", prefsFile.exists())
        val raw = prefsFile.readText()
        assertFalse("key không được nằm plaintext trong ${prefsFile.name}", raw.contains("AIzaPLAINTEXT_SHOULD_NOT_APPEAR"))

        store.clear()
    }
}