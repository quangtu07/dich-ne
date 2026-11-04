package vn.bubbledict.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BE-33 / F14: phần thuần logic của [OfflineModelManager] (không gọi Play Services).
 * Luật ML Kit: model theo từng ngôn ngữ ≠ EN, pivot qua EN.
 */
class OfflineModelManagerTest {

    @Test
    fun enToVi_needsVietnameseModelOnly() {
        assertEquals(listOf("vi"), OfflineModelManager.requiredModelsFor("en", "vi"))
    }

    @Test
    fun viToEn_needsVietnameseModelOnly() {
        assertEquals(listOf("vi"), OfflineModelManager.requiredModelsFor("vi", "en"))
    }

    @Test
    fun zhToVi_needsBothViaEnglishPivot() {
        assertEquals(listOf("zh", "vi"), OfflineModelManager.requiredModelsFor("zh", "vi"))
    }

    @Test
    fun koToJa_needsBoth() {
        assertEquals(listOf("ko", "ja"), OfflineModelManager.requiredModelsFor("ko", "ja"))
    }

    @Test
    fun enToEn_needsNoModel() {
        assertEquals(emptyList<String>(), OfflineModelManager.requiredModelsFor("en", "en"))
    }

    @Test
    fun unsupportedLanguage_isIgnored() {
        // "fr" ngoài phạm vi 1.0.5 -> chỉ còn model của phía hợp lệ.
        assertEquals(listOf("vi"), OfflineModelManager.requiredModelsFor("fr", "vi"))
        assertEquals(emptyList<String>(), OfflineModelManager.requiredModelsFor("fr", "en"))
    }

    @Test
    fun isoCodes_mapToMlKitLanguages() {
        assertEquals("en", OfflineModelManager.mlKitLanguage("en"))
        assertEquals("vi", OfflineModelManager.mlKitLanguage("VI"))
        assertEquals("zh", OfflineModelManager.mlKitLanguage("zh"))
        assertEquals("ko", OfflineModelManager.mlKitLanguage("ko"))
        assertEquals("ja", OfflineModelManager.mlKitLanguage("ja"))
        assertNull(OfflineModelManager.mlKitLanguage("fr"))
    }
}